package com.a.eye.skywalking.collector.worker.web.persistence;

import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.index.ApplicationIndexWithDagNodeType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.builder.api.FilterComponentBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.global.GlobalAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
public class ApplicationPersistence {

    private Logger logger = LogManager.getFormatterLogger(ApplicationPersistence.class);

    public void searchDagNode(long startTimeSlice, long endTimeSlice){
        SearchRequestBuilder searchRequestBuilder = EsClient.getClient().prepareSearch(ApplicationIndexWithDagNodeType.Index);
        searchRequestBuilder.setTypes(ApplicationIndexWithDagNodeType.Type);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

//        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery("timeSlice").gte(startTimeSlice).lte(endTimeSlice));
        ConstantScoreQueryBuilder constantScoreQueryBuilder = QueryBuilders.constantScoreQuery(QueryBuilders.rangeQuery("timeSlice").gte(startTimeSlice).lte(endTimeSlice));
        searchRequestBuilder.setQuery(constantScoreQueryBuilder);

////        GlobalAggregationBuilder aggregationBuilder = AggregationBuilders.global("agg").subAggregation(AggregationBuilders.terms("distinct_code").field("code"));
//        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms("distinct_code").field("code");
        searchRequestBuilder.addAggregation(AggregationBuilders.terms("distinct_code").field("code"));
        SearchResponse response = searchRequestBuilder.execute().actionGet();
        List<Aggregation> aggregationList = response.getAggregations().asList();
        logger.debug("dag node list size: %s", aggregationList.size());

        for (Aggregation aggregation : aggregationList) {
            for (Map.Entry<String, Object> entry : aggregation.getMetaData().entrySet()) {
                logger.debug("code: %s, count: %s", entry.getKey(), entry.getValue());
            }
        }
    }
}
