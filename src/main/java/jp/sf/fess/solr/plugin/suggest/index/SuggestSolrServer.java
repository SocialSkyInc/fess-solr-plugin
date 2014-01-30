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

import java.io.IOException;
import java.util.List;

import jp.sf.fess.suggest.SuggestConstants;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SuggestSolrServer {
    private static final Logger logger = LoggerFactory
            .getLogger(SuggestSolrServer.class);

    private SolrServer server;

    protected SuggestSolrServer(final String url) {
        try {
            final HttpSolrServer server = new HttpSolrServer(url);
            server.setConnectionTimeout(10 * 1000);
            server.setMaxRetries(3);
            this.server = server;
        } catch (final Exception e) {
            logger.warn("Failed to create SuggestSolrServer object.", e);
        }
    }

    public SuggestSolrServer(final SolrServer server) {
        this.server = server;
    }

    public void add(final SolrInputDocument doc) throws IOException,
            SolrServerException {
        server.add(doc);
    }

    public void add(final List<SolrInputDocument> documents)
            throws IOException, SolrServerException {
        server.add(documents);
    }

    public void commit() throws IOException, SolrServerException {
        server.commit();
    }

    public void deleteAll() throws IOException, SolrServerException {
        server.deleteByQuery("*:*");
    }

    public void deleteByQuery(final String query) throws IOException,
            SolrServerException {
        server.deleteByQuery(query);
    }

    public SolrDocumentList select(final String query) throws IOException,
            SolrServerException {

        final SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(query);
        solrQuery.setFields(new String[] { "id",
                SuggestConstants.SuggestFieldNames.COUNT,
                SuggestConstants.SuggestFieldNames.LABELS,
                SuggestConstants.SuggestFieldNames.ROLES,
                SuggestConstants.SuggestFieldNames.FIELD_NAME });
        final QueryResponse queryResponse = server.query(solrQuery,
                SolrRequest.METHOD.POST);
        return queryResponse.getResults();
    }

    public SolrDocumentList get(final String ids) throws IOException,
            SolrServerException {
        final SolrQuery solrQuery = new SolrQuery();
        solrQuery.setRequestHandler("/get");
        solrQuery.set("ids", ids);
        final QueryResponse response = server.query(solrQuery,
                SolrRequest.METHOD.POST);
        return response.getResults();
    }
}
