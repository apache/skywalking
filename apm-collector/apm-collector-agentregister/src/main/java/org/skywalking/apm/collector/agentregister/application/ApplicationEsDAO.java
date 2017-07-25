package org.skywalking.apm.collector.agentregister.application;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.skywalking.apm.collector.agentstream.worker.register.application.ApplicationRegisterTable;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;

/**
 * @author pengys5
 */
public class ApplicationEsDAO extends EsDAO implements IApplicationDAO {

    @Override public int getApplicationId(String applicationCode) {
        ElasticSearchClient client = getClient();

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(ApplicationRegisterTable.TABLE);
        searchRequestBuilder.setTypes("type");
        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.termQuery(ApplicationRegisterTable.COLUMN_APPLICATION_CODE, applicationCode));
        searchRequestBuilder.setSize(10);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (searchResponse.getHits().totalHits > 0) {
            return searchResponse.getHits().getAt(0).getField(ApplicationRegisterTable.COLUMN_APPLICATION_ID).getValue();
        }
        return 0;
    }
}
