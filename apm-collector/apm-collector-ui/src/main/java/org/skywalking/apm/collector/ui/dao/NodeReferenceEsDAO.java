package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.skywalking.apm.collector.stream.worker.util.Const;
import org.skywalking.apm.collector.agentstream.worker.noderef.reference.define.NodeRefTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class NodeReferenceEsDAO extends EsDAO implements INodeReferenceDAO {

    private final Logger logger = LoggerFactory.getLogger(NodeReferenceEsDAO.class);

    @Override public JsonArray load(long startTime, long endTime) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(NodeRefTable.TABLE);
        searchRequestBuilder.setTypes(NodeRefTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeRefTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(AggregationBuilders.terms(NodeRefTable.COLUMN_AGG).field(NodeRefTable.COLUMN_AGG).size(100));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        Terms genders = searchResponse.getAggregations().get(NodeRefTable.COLUMN_AGG);

        JsonArray nodeRefArray = new JsonArray();
        for (Terms.Bucket entry : genders.getBuckets()) {
            String aggId = entry.getKeyAsString();
            String[] aggIds = aggId.split(Const.IDS_SPLIT);
            String front = aggIds[0];
            String behind = aggIds[1];

            JsonObject nodeRefObj = new JsonObject();
            nodeRefObj.addProperty("front", front);
            nodeRefObj.addProperty("behind", behind);
            nodeRefArray.add(nodeRefObj);
        }
        logger.debug("node ref data: {}", nodeRefArray.toString());
        return nodeRefArray;
    }
}
