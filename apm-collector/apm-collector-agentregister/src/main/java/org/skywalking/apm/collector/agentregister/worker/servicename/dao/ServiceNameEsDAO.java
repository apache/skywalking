package org.skywalking.apm.collector.agentregister.worker.servicename.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.define.register.ServiceNameDataDefine;
import org.skywalking.apm.collector.storage.define.register.ServiceNameTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ServiceNameEsDAO extends EsDAO implements IServiceNameDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameEsDAO.class);

    @Override public int getServiceId(int applicationId, String serviceName) {
        ElasticSearchClient client = getClient();

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(ServiceNameTable.TABLE);
        searchRequestBuilder.setTypes(ServiceNameTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
        BoolQueryBuilder builder = QueryBuilders.boolQuery();
        builder.must().add(QueryBuilders.termQuery(ServiceNameTable.COLUMN_APPLICATION_ID, applicationId));
        builder.must().add(QueryBuilders.termQuery(ServiceNameTable.COLUMN_SERVICE_NAME, serviceName));
        searchRequestBuilder.setQuery(builder);
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            int serviceId = (int)searchHit.getSource().get(ServiceNameTable.COLUMN_SERVICE_ID);
            return serviceId;
        }
        return 0;
    }

    @Override public int getMaxServiceId() {
        return getMaxId(ServiceNameTable.TABLE, ServiceNameTable.COLUMN_SERVICE_ID);
    }

    @Override public int getMinServiceId() {
        return getMinId(ServiceNameTable.TABLE, ServiceNameTable.COLUMN_SERVICE_ID);
    }

    @Override public String getServiceName(int serviceId) {
        GetResponse response = getClient().prepareGet(ServiceNameTable.TABLE, String.valueOf(serviceId)).get();
        if (response.isExists()) {
            return (String)response.getSource().get(ServiceNameTable.COLUMN_SERVICE_NAME);
        } else {
            return Const.EMPTY_STRING;
        }
    }

    @Override public void save(ServiceNameDataDefine.ServiceName serviceName) {
        logger.debug("save service name register info, application id: {}, service name: {}", serviceName.getApplicationId(), serviceName.getServiceName());
        ElasticSearchClient client = getClient();
        Map<String, Object> source = new HashMap();
        source.put(ServiceNameTable.COLUMN_SERVICE_ID, serviceName.getServiceId());
        source.put(ServiceNameTable.COLUMN_APPLICATION_ID, serviceName.getApplicationId());
        source.put(ServiceNameTable.COLUMN_SERVICE_NAME, serviceName.getServiceName());

        IndexResponse response = client.prepareIndex(ServiceNameTable.TABLE, serviceName.getId()).setSource(source).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
        logger.debug("save service name register info, application id: {}, service name: {}, status: {}", serviceName.getApplicationId(), serviceName.getServiceName(), response.status().name());
    }
}
