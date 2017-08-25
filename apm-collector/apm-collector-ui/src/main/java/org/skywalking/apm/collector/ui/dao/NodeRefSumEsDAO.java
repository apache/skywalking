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
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.define.noderef.NodeRefSumTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.ui.cache.ApplicationCache;
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

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(NodeRefSumTable.COLUMN_FRONT_APPLICATION_ID).field(NodeRefSumTable.COLUMN_FRONT_APPLICATION_ID).size(100);
        aggregationBuilder.subAggregation(AggregationBuilders.terms(NodeRefSumTable.COLUMN_BEHIND_APPLICATION_ID).field(NodeRefSumTable.COLUMN_BEHIND_APPLICATION_ID).size(100)
            .subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_S1_LTE).field(NodeRefSumTable.COLUMN_S1_LTE))
            .subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_S3_LTE).field(NodeRefSumTable.COLUMN_S3_LTE))
            .subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_S5_LTE).field(NodeRefSumTable.COLUMN_S5_LTE))
            .subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_S5_GT).field(NodeRefSumTable.COLUMN_S5_GT))
            .subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_SUMMARY).field(NodeRefSumTable.COLUMN_SUMMARY))
            .subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_ERROR).field(NodeRefSumTable.COLUMN_ERROR)));
        aggregationBuilder.subAggregation(AggregationBuilders.terms(NodeRefSumTable.COLUMN_BEHIND_PEER).field(NodeRefSumTable.COLUMN_BEHIND_PEER).size(100)
            .subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_S1_LTE).field(NodeRefSumTable.COLUMN_S1_LTE))
            .subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_S3_LTE).field(NodeRefSumTable.COLUMN_S3_LTE))
            .subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_S5_LTE).field(NodeRefSumTable.COLUMN_S5_LTE))
            .subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_S5_GT).field(NodeRefSumTable.COLUMN_S5_GT))
            .subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_SUMMARY).field(NodeRefSumTable.COLUMN_SUMMARY))
            .subAggregation(AggregationBuilders.sum(NodeRefSumTable.COLUMN_ERROR).field(NodeRefSumTable.COLUMN_ERROR)));

        searchRequestBuilder.addAggregation(aggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        JsonArray nodeRefResSumArray = new JsonArray();
        Terms frontApplicationIdTerms = searchResponse.getAggregations().get(NodeRefSumTable.COLUMN_FRONT_APPLICATION_ID);
        for (Terms.Bucket frontApplicationIdBucket : frontApplicationIdTerms.getBuckets()) {
            int applicationId = frontApplicationIdBucket.getKeyAsNumber().intValue();
            String applicationCode = ApplicationCache.getForUI(applicationId);
            Terms behindApplicationIdTerms = frontApplicationIdBucket.getAggregations().get(NodeRefSumTable.COLUMN_BEHIND_APPLICATION_ID);
            for (Terms.Bucket behindApplicationIdBucket : behindApplicationIdTerms.getBuckets()) {
                int behindApplicationId = behindApplicationIdBucket.getKeyAsNumber().intValue();

                if (behindApplicationId != 0) {
                    String behindApplicationCode = ApplicationCache.getForUI(behindApplicationId);

                    Sum s1LTE = behindApplicationIdBucket.getAggregations().get(NodeRefSumTable.COLUMN_S1_LTE);
                    Sum s3LTE = behindApplicationIdBucket.getAggregations().get(NodeRefSumTable.COLUMN_S3_LTE);
                    Sum s5LTE = behindApplicationIdBucket.getAggregations().get(NodeRefSumTable.COLUMN_S5_LTE);
                    Sum s5GT = behindApplicationIdBucket.getAggregations().get(NodeRefSumTable.COLUMN_S5_GT);
                    Sum summary = behindApplicationIdBucket.getAggregations().get(NodeRefSumTable.COLUMN_SUMMARY);
                    Sum error = behindApplicationIdBucket.getAggregations().get(NodeRefSumTable.COLUMN_ERROR);
                    logger.debug("applicationId: {}, behindApplicationId: {}, s1LTE: {}, s3LTE: {}, s5LTE: {}, s5GT: {}, error: {}, summary: {}", applicationId,
                        behindApplicationId, s1LTE.getValue(), s3LTE.getValue(), s5LTE.getValue(), s5GT.getValue(), error.getValue(), summary.getValue());

                    JsonObject nodeRefResSumObj = new JsonObject();
                    nodeRefResSumObj.addProperty("front", applicationCode);
                    nodeRefResSumObj.addProperty("behind", behindApplicationCode);
                    nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_S1_LTE, s1LTE.getValue());
                    nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_S3_LTE, s3LTE.getValue());
                    nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_S5_LTE, s5LTE.getValue());
                    nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_S5_GT, s5GT.getValue());
                    nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_ERROR, error.getValue());
                    nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_SUMMARY, summary.getValue());
                    nodeRefResSumArray.add(nodeRefResSumObj);
                }
            }

            Terms behindPeerTerms = frontApplicationIdBucket.getAggregations().get(NodeRefSumTable.COLUMN_BEHIND_PEER);
            for (Terms.Bucket behindPeerBucket : behindPeerTerms.getBuckets()) {
                String behindPeer = behindPeerBucket.getKeyAsString();

                if (StringUtils.isNotEmpty(behindPeer)) {
                    Sum s1LTE = behindPeerBucket.getAggregations().get(NodeRefSumTable.COLUMN_S1_LTE);
                    Sum s3LTE = behindPeerBucket.getAggregations().get(NodeRefSumTable.COLUMN_S3_LTE);
                    Sum s5LTE = behindPeerBucket.getAggregations().get(NodeRefSumTable.COLUMN_S5_LTE);
                    Sum s5GT = behindPeerBucket.getAggregations().get(NodeRefSumTable.COLUMN_S5_GT);
                    Sum summary = behindPeerBucket.getAggregations().get(NodeRefSumTable.COLUMN_SUMMARY);
                    Sum error = behindPeerBucket.getAggregations().get(NodeRefSumTable.COLUMN_ERROR);
                    logger.debug("applicationId: {}, behindPeer: {}, s1LTE: {}, s3LTE: {}, s5LTE: {}, s5GT: {}, error: {}, summary: {}", applicationId,
                        behindPeer, s1LTE.getValue(), s3LTE.getValue(), s5LTE.getValue(), s5GT.getValue(), error.getValue(), summary.getValue());

                    JsonObject nodeRefResSumObj = new JsonObject();
                    nodeRefResSumObj.addProperty("front", applicationCode);
                    nodeRefResSumObj.addProperty("behind", behindPeer);
                    nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_S1_LTE, s1LTE.getValue());
                    nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_S3_LTE, s3LTE.getValue());
                    nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_S5_LTE, s5LTE.getValue());
                    nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_S5_GT, s5GT.getValue());
                    nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_ERROR, error.getValue());
                    nodeRefResSumObj.addProperty(NodeRefSumTable.COLUMN_SUMMARY, summary.getValue());
                    nodeRefResSumArray.add(nodeRefResSumObj);
                }
            }
        }

        return nodeRefResSumArray;
    }
}
