package org.skywalking.apm.collector.agentregister.worker.application.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.skywalking.apm.collector.storage.define.register.ApplicationDataDefine;
import org.skywalking.apm.collector.storage.define.register.ApplicationTable;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ApplicationEsDAO extends EsDAO implements IApplicationDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationEsDAO.class);

    @Override public int getApplicationId(String applicationCode) {
        ElasticSearchClient client = getClient();

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(ApplicationTable.TABLE);
        searchRequestBuilder.setTypes("type");
        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.termQuery(ApplicationTable.COLUMN_APPLICATION_CODE, applicationCode));
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            int applicationId = (int)searchHit.getSource().get(ApplicationTable.COLUMN_APPLICATION_ID);
            return applicationId;
        }
        return 0;
    }

    @Override public int getMaxApplicationId() {
        return getMaxId(ApplicationTable.TABLE, ApplicationTable.COLUMN_APPLICATION_ID);
    }

    @Override public int getMinApplicationId() {
        return getMinId(ApplicationTable.TABLE, ApplicationTable.COLUMN_APPLICATION_ID);
    }

    @Override public void save(ApplicationDataDefine.Application application) {
        logger.debug("save application register info, application id: {}, application code: {}", application.getApplicationId(), application.getApplicationCode());
        ElasticSearchClient client = getClient();
        Map<String, Object> source = new HashMap();
        source.put(ApplicationTable.COLUMN_APPLICATION_CODE, application.getApplicationCode());
        source.put(ApplicationTable.COLUMN_APPLICATION_ID, application.getApplicationId());

        IndexResponse response = client.prepareIndex(ApplicationTable.TABLE, application.getId()).setSource(source).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
        logger.debug("save application register info, application id: {}, application code: {}, status: {}", application.getApplicationId(), application.getApplicationCode(), response.status().name());
    }
}
