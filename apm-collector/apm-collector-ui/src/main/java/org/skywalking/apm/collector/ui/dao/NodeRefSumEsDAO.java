package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.skywalking.apm.collector.stream.worker.util.Const;
import org.skywalking.apm.collector.agentstream.worker.noderef.summary.define.NodeRefSumTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class NodeRefSumEsDAO extends EsDAO implements INodeRefSumDAO {

    private final Logger logger = LoggerFactory.getLogger(NodeRefSumEsDAO.class);

    @Override public JsonArray load(long startTime, long endTime) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(NodeRefSumTable.TABLE);
        searchRequestBuilder.setTypes(NodeRefSumTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeRefSumTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        searchRequestBuilder.setSize(0);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(NodeRefSumTable.COLUMN_AGG).field(NodeRefSumTable.COLUMN_AGG);
        aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_ONE_SECOND_LESS).field(NodeRefSumTable.COLUMN_ONE_SECOND_LESS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_THREE_SECOND_LESS).field(NodeRefSumTable.COLUMN_THREE_SECOND_LESS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_FIVE_SECOND_LESS).field(NodeRefSumTable.COLUMN_FIVE_SECOND_LESS));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_FIVE_SECOND_GREATER).field(NodeRefSumTable.COLUMN_FIVE_SECOND_GREATER));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_ERROR).field(NodeRefSumTable.COLUMN_ERROR));
        aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_SUMMARY).field(NodeRefSumTable.COLUMN_SUMMARY));

        searchRequestBuilder.addAggregation(aggregationBuilder);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        JsonArray nodeRefResSumArray = new JsonArray();
        Terms aggTerms = searchResponse.getAggregations().get(NodeRefSumTable.COLUMN_AGG);
        for (Terms.Bucket bucket : aggTerms.getBuckets()) {
            String aggId = String.valueOf(bucket.getKey());
            Sum oneSecondLess = bucket.getAggregations().get(NodeRefSumTable.COLUMN_ONE_SECOND_LESS);
            Sum threeSecondLess = bucket.getAggregations().get(NodeRefSumTable.COLUMN_THREE_SECOND_LESS);
            Sum fiveSecondLess = bucket.getAggregations().get(NodeRefSumTable.COLUMN_FIVE_SECOND_LESS);
            Sum fiveSecondGreater = bucket.getAggregations().get(NodeRefSumTable.COLUMN_FIVE_SECOND_GREATER);
            Sum error = bucket.getAggregations().get(NodeRefSumTable.COLUMN_ERROR);
            Sum summary = bucket.getAggregations().get(NodeRefSumTable.COLUMN_SUMMARY);
            logger.debug("aggId: {}, oneSecondLess: {}, threeSecondLess: {}, fiveSecondLess: {}, fiveSecondGreater: {}, error: {}, summary: {}", aggId,
                oneSecondLess.getValue(), threeSecondLess.getValue(), fiveSecondLess.getValue(), fiveSecondGreater.getValue(), error.getValue(), summary.getValue());

            JsonObject nodeRefResSumObj = new JsonObject();
            String[] ids = aggId.split(Const.IDS_SPLIT);
            String front = ids[0];
            String behind = ids[1];

            nodeRefResSumObj.addProperty("front", front);
            nodeRefResSumObj.addProperty("behind", behind);

            nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_ONE_SECOND_LESS, oneSecondLess.getValue());
            nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_THREE_SECOND_LESS, threeSecondLess.getValue());
            nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_FIVE_SECOND_LESS, fiveSecondLess.getValue());
            nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_FIVE_SECOND_GREATER, fiveSecondGreater.getValue());
            nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_ERROR, error.getValue());
            nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_SUMMARY, summary.getValue());
            nodeRefResSumArray.add(nodeRefResSumObj);
        }

        return nodeRefResSumArray;
    }
}
