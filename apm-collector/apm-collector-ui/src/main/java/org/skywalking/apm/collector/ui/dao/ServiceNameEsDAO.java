package org.skywalking.apm.collector.ui.dao;

import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.define.register.ServiceNameTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;

/**
 * @author pengys5
 */
public class ServiceNameEsDAO extends EsDAO implements IServiceNameDAO {

    @Override public String getServiceName(int serviceId) {
        GetRequestBuilder getRequestBuilder = getClient().prepareGet(ServiceNameTable.TABLE, String.valueOf(serviceId));

        GetResponse getResponse = getRequestBuilder.get();
        if (getResponse.isExists()) {
            return (String)getResponse.getSource().get(ServiceNameTable.COLUMN_SERVICE_NAME);
        }
        return Const.UNKNOWN;
    }

    @Override public int getServiceId(int applicationId, String serviceName) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceNameTable.TABLE);
        searchRequestBuilder.setTypes(ServiceNameTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.matchQuery(ServiceNameTable.COLUMN_APPLICATION_ID, applicationId));
        boolQuery.must().add(QueryBuilders.matchQuery(ServiceNameTable.COLUMN_SERVICE_NAME, serviceName));
        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.get();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            int serviceId = (int)searchHit.getSource().get(ServiceNameTable.COLUMN_SERVICE_ID);
            return serviceId;
        }
        return 0;
    }
}
