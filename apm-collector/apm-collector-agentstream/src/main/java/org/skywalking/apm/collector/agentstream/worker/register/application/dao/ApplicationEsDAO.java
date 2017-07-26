package org.skywalking.apm.collector.agentstream.worker.register.application.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.aggregations.metrics.max.MaxAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.min.Min;
import org.elasticsearch.search.aggregations.metrics.min.MinAggregationBuilder;
import org.skywalking.apm.collector.agentstream.worker.register.application.ApplicationDataDefine;
import org.skywalking.apm.collector.agentstream.worker.register.application.ApplicationTable;
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
        ElasticSearchClient client = getClient();
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(ApplicationTable.TABLE);
        searchRequestBuilder.setTypes("type");
        searchRequestBuilder.setSize(0);
        MaxAggregationBuilder aggregation = AggregationBuilders.max("agg").field(ApplicationTable.COLUMN_APPLICATION_ID);
        searchRequestBuilder.addAggregation(aggregation);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        Max agg = searchResponse.getAggregations().get("agg");

        int id = (int)agg.getValue();
        if (id == Integer.MAX_VALUE || id == Integer.MIN_VALUE) {
            return 0;
        } else {
            return id;
        }
    }

    @Override public int getMinApplicationId() {
        ElasticSearchClient client = getClient();
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(ApplicationTable.TABLE);
        searchRequestBuilder.setTypes("type");
        searchRequestBuilder.setSize(0);
        MinAggregationBuilder aggregation = AggregationBuilders.min("agg").field(ApplicationTable.COLUMN_APPLICATION_ID);
        searchRequestBuilder.addAggregation(aggregation);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        Min agg = searchResponse.getAggregations().get("agg");

        int id = (int)agg.getValue();
        if (id == Integer.MAX_VALUE || id == Integer.MIN_VALUE) {
            return 0;
        } else {
            return id;
        }
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
