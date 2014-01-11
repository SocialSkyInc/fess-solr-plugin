package jp.sf.fess.solr.plugin.suggest.index;


import jp.sf.fess.solr.plugin.suggest.TestUtils;
import junit.framework.TestCase;
import org.apache.solr.common.SolrInputDocument;

public class SuggestSolrServerTest extends TestCase {
    @Override
    public void setUp() throws Exception{
        super.setUp();
        TestUtils.startJerrySolrRunner();
    }

    @Override
    public void tearDown() throws Exception{
        super.tearDown();
        TestUtils.stopJettySolrRunner();
    }

    public void test_add() {
        SuggestSolrServer suggestSolrServer = new SuggestSolrServer(TestUtils.SOLR_URL);
        SolrInputDocument doc = new SolrInputDocument();
        doc.setField("content_s", "aaaa");
        doc.setField("id", "1");

        try {
            suggestSolrServer.deleteAll();
            suggestSolrServer.add(doc);
            suggestSolrServer.commit();
            assertTrue(suggestSolrServer.select("*:*").getNumFound() == 1);
            suggestSolrServer.deleteAll();
            suggestSolrServer.commit();
            assertTrue(suggestSolrServer.select("*:*").getNumFound() == 0);
        } catch(Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

    }
}
