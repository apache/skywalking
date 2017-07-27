package org.skywalking.apm.collector.storage.elasticsearch.dao;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.aggregations.metrics.max.MaxAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.min.Min;
import org.elasticsearch.search.aggregations.metrics.min.MinAggregationBuilder;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.storage.dao.DAO;

/**
 * @author pengys5
 */
public abstract class EsDAO extends DAO<ElasticSearchClient> {

    public final int getMaxId(String indexName, String columnName) {
        ElasticSearchClient client = getClient();
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(indexName);
        searchRequestBuilder.setTypes("type");
        searchRequestBuilder.setSize(0);
        MaxAggregationBuilder aggregation = AggregationBuilders.max("agg").field(columnName);
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

    public final int getMinId(String indexName, String columnName) {
        ElasticSearchClient client = getClient();
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(indexName);
        searchRequestBuilder.setTypes("type");
        searchRequestBuilder.setSize(0);
        MinAggregationBuilder aggregation = AggregationBuilders.min("agg").field(columnName);
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
}
