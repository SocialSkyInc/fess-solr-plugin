/*
 * Copyright 2009-2014 the CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package jp.sf.fess.solr.plugin.suggest.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jp.sf.fess.solr.plugin.suggest.entity.SuggestItem;
import jp.sf.fess.solr.plugin.suggest.enums.RequestType;
import jp.sf.fess.suggest.SuggestConstants;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexUpdater extends Thread {
    private static final Logger logger = LoggerFactory
            .getLogger(IndexUpdater.class);

    protected Queue<Request> suggestRequestQueue = new ConcurrentLinkedQueue<Request>();

    protected final SuggestSolrServer suggestSolrServer;

    protected AtomicBoolean running = new AtomicBoolean(false);

    protected AtomicLong updateInterval = new AtomicLong(10 * 1000);

    protected AtomicInteger maxUpdateNum = new AtomicInteger(10000);

    public IndexUpdater(final SuggestSolrServer suggestSolrServer) {
        this.suggestSolrServer = suggestSolrServer;
    }

    public void setUpdateInterval(final long updateInterval) {
        this.updateInterval.set(updateInterval);
    }

    public int getQueuingItemNum() {
        return suggestRequestQueue.size();
    }

    public void addSuggestItem(final SuggestItem item) {
        final Request request = new Request(RequestType.ADD, item);
        suggestRequestQueue.add(request);
        synchronized (suggestSolrServer) {
            suggestSolrServer.notify();
        }
    }

    public void commit() {
        final Request request = new Request(RequestType.COMMIT, null);
        suggestRequestQueue.add(request);
        synchronized (suggestSolrServer) {
            suggestSolrServer.notify();
        }
    }

    public void deleteByQuery(final String query) {
        final Request request = new Request(RequestType.DELETE_BY_QUERY, query);
        suggestRequestQueue.add(request);
        synchronized (suggestSolrServer) {
            suggestSolrServer.notify();
        }
    }

    public void close() {
        running.set(false);
    }

    @Override
    public void run() {
        if (logger.isInfoEnabled()) {
            logger.info("Start indexUpdater");
        }
        running.set(true);
        boolean doCommit = false;
        while (running.get()) {
            final int max = maxUpdateNum.get();
            final Request[] requestArray = new Request[max];
            int requestNum = 0;
            for (int i = 0; i < max; i++) {
                final Request request = suggestRequestQueue.peek();
                if (request == null) {
                    break;
                }
                if (request.type == RequestType.ADD) {
                    suggestRequestQueue.poll();

                    //merge duplicate items
                    boolean exist = false;
                    final SuggestItem item2 = (SuggestItem) request.obj;
                    for (int j = 0; j < requestNum; j++) {
                        final SuggestItem item1 = (SuggestItem) requestArray[j].obj;
                        if (item1.equals(item2)) {
                            mergeSuggestItem(item1, item2);
                            exist = true;
                            break;
                        }
                    }
                    if (!exist) {
                        requestArray[requestNum] = request;
                        requestNum++;
                    }
                } else if (requestNum == 0) {
                    requestArray[requestNum] = suggestRequestQueue.poll();
                    requestNum++;
                    break;
                } else {
                    break;
                }
            }

            if (requestNum == 0) {
                try {
                    //commit if needed
                    if (doCommit) {
                        suggestSolrServer.commit();
                        doCommit = false;
                    }
                    try {
                        //wait next item...
                        synchronized (suggestSolrServer) {
                            suggestSolrServer.wait(updateInterval.get());
                        }
                    } catch (final InterruptedException e) {
                        break;
                    }
                } catch (final Exception e) {
                    //ignore
                }
                continue;
            }
            doCommit = true;

            switch (requestArray[0].type) {
            case ADD:
                final long start = System.currentTimeMillis();
                if (logger.isDebugEnabled()) {
                    logger.debug("Add " + requestNum + "documents");
                }

                final SuggestItem[] suggestItemArray = new SuggestItem[requestNum];
                int itemSize = 0;

                final StringBuilder ids = new StringBuilder(100000);
                for (int i = 0; i < requestNum; i++) {
                    final Request request = requestArray[i];
                    final SuggestItem item = (SuggestItem) request.obj;
                    suggestItemArray[itemSize] = item;
                    if (ids.length() > 0) {
                        ids.append(',');
                    }
                    ids.append(item.getDocumentId());
                    itemSize++;
                }

                mergeSolrIndex(suggestItemArray, itemSize, ids.toString());

                final List<SolrInputDocument> solrInputDocumentList = new ArrayList<SolrInputDocument>(
                        itemSize);
                for (int i = 0; i < itemSize; i++) {
                    final SuggestItem item = suggestItemArray[i];
                    solrInputDocumentList.add(item.toSolrInputDocument());
                }
                try {
                    suggestSolrServer.add(solrInputDocumentList);
                    if (logger.isInfoEnabled()) {
                        logger.info("Done add " + itemSize + " terms. took: "
                                + (System.currentTimeMillis() - start));
                    }
                } catch (final Exception e) {
                    logger.warn("Failed to add document.", e);
                }
                break;
            case COMMIT:
                if (logger.isDebugEnabled()) {
                    logger.debug("Commit.");
                }
                try {
                    suggestSolrServer.commit();
                    doCommit = false;
                } catch (final Exception e) {
                    logger.warn("Failed to commit.", e);
                }
                break;
            case DELETE_BY_QUERY:
                if (logger.isInfoEnabled()) {
                    logger.info("DeleteByQuery. query="
                            + requestArray[0].obj.toString());
                }
                try {
                    suggestSolrServer
                            .deleteByQuery((String) requestArray[0].obj);
                    suggestSolrServer.commit();
                } catch (final Exception e) {
                    logger.warn("Failed to deleteByQuery.", e);
                }
                break;
            default:
                break;
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("Stop IndexUpdater");
            suggestRequestQueue.clear();
        }
    }

    protected void mergeSolrIndex(final SuggestItem[] suggestItemArray,
            final int itemSize, final String ids) {
        final long startTime = System.currentTimeMillis();
        if (itemSize > 0) {
            SolrDocumentList documentList = null;
            try {
                documentList = suggestSolrServer.get(ids);
                if (logger.isDebugEnabled()) {
                    logger.debug("search end. " + "getNum="
                            + documentList.size() + "  took:"
                            + (System.currentTimeMillis() - startTime));
                }
            } catch (final Exception e) {
                logger.warn("Failed merge solr index.", e);
            }
            if (documentList != null) {
                int itemCount = 0;
                for (final SolrDocument doc : documentList) {
                    final Object idObj = doc.getFieldValue("id");
                    if (idObj == null) {
                        continue;
                    }
                    final String id = idObj.toString();

                    for (; itemCount < itemSize; itemCount++) {
                        final SuggestItem item = suggestItemArray[itemCount];

                        if (item.getDocumentId().equals(id)) {
                            final Object count = doc
                                    .getFieldValue(SuggestConstants.SuggestFieldNames.COUNT);
                            if (count != null) {
                                item.setCount(item.getCount()
                                        + Long.parseLong(count.toString()));
                            }
                            final Collection<Object> labels = doc
                                    .getFieldValues(SuggestConstants.SuggestFieldNames.LABELS);
                            if (labels != null) {
                                final List<String> itemLabelList = item
                                        .getLabels();
                                for (final Object label : labels) {
                                    if (!itemLabelList.contains(label
                                            .toString())) {
                                        itemLabelList.add(label.toString());
                                    }
                                }
                            }
                            final Collection<Object> roles = doc
                                    .getFieldValues(SuggestConstants.SuggestFieldNames.ROLES);
                            if (roles != null) {
                                final List<String> itemRoleList = item
                                        .getRoles();
                                for (final Object role : roles) {
                                    if (!itemRoleList.contains(role.toString())) {
                                        itemRoleList.add(role.toString());
                                    }
                                }
                            }
                            final Collection<Object> fields = doc
                                    .getFieldValues(SuggestConstants.SuggestFieldNames.FIELD_NAME);
                            if (fields != null) {
                                final List<String> fieldNameList = item
                                        .getFieldNameList();
                                for (final Object field : fields) {
                                    if (!fieldNameList.contains(field
                                            .toString())) {
                                        fieldNameList.add(field.toString());
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    protected void mergeSuggestItem(final SuggestItem item1,
            final SuggestItem item2) {
        item1.setCount(item1.getCount() + 1);
        item1.setExpires(item2.getExpires());
        item1.setSegment(item2.getSegment());
        final List<String> fieldNameList = item1.getFieldNameList();
        for (final String fieldName : item2.getFieldNameList()) {
            if (!fieldNameList.contains(fieldName)) {
                fieldNameList.add(fieldName);
            }
        }
        final List<String> labelList = item1.getLabels();
        for (final String label : item2.getLabels()) {
            if (!labelList.contains(label)) {
                labelList.add(label);
            }
        }
        final List<String> roleList = item1.getRoles();
        for (final String role : item2.getRoles()) {
            if (!roleList.contains(role)) {
                roleList.add(role);
            }
        }
    }

    protected static class Request {
        public RequestType type;

        public Object obj;

        public Request(final RequestType type, final Object o) {
            this.type = type;
            obj = o;
        }
    }
}
