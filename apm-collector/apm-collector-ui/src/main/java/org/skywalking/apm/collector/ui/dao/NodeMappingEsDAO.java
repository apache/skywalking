package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.storage.define.node.NodeMappingTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class NodeMappingEsDAO extends EsDAO implements INodeMappingDAO {

    private final Logger logger = LoggerFactory.getLogger(NodeMappingEsDAO.class);

    @Override public JsonArray load(long startTime, long endTime) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(NodeMappingTable.TABLE);
        searchRequestBuilder.setTypes(NodeMappingTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeMappingTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(AggregationBuilders.terms(NodeMappingTable.COLUMN_AGG).field(NodeMappingTable.COLUMN_AGG).size(100));
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        Terms genders = searchResponse.getAggregations().get(NodeMappingTable.COLUMN_AGG);

        JsonArray nodeMappingArray = new JsonArray();
        for (Terms.Bucket entry : genders.getBuckets()) {
            String aggId = entry.getKeyAsString();
            String[] aggIds = aggId.split(Const.IDS_SPLIT);
            String code = aggIds[0];
            String peers = aggIds[1];

            JsonObject nodeMappingObj = new JsonObject();
            nodeMappingObj.addProperty("code", code);
            nodeMappingObj.addProperty("peers", peers);
            nodeMappingArray.add(nodeMappingObj);
        }
        logger.debug("node mapping data: {}", nodeMappingArray.toString());
        return nodeMappingArray;
    }
}
