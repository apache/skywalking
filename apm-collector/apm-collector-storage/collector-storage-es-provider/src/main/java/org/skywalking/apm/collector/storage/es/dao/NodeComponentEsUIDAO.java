/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.es.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.storage.dao.INodeComponentUIDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.node.NodeComponentTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NodeComponentEsUIDAO extends EsDAO implements INodeComponentUIDAO {

    private final Logger logger = LoggerFactory.getLogger(NodeComponentEsPersistenceDAO.class);

    public NodeComponentEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public JsonArray load(long startTime, long endTime) {
        logger.debug("node component load, start time: {}, end time: {}", startTime, endTime);
        JsonArray nodeComponentArray = new JsonArray();
        nodeComponentArray.addAll(aggregationByComponentId(startTime, endTime));
        return nodeComponentArray;
    }

    private JsonArray aggregationByComponentId(long startTime, long endTime) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(NodeComponentTable.TABLE);
        searchRequestBuilder.setTypes(NodeComponentTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeComponentTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(AggregationBuilders.terms(NodeComponentTable.COLUMN_COMPONENT_ID).field(NodeComponentTable.COLUMN_COMPONENT_ID).size(100)
            .subAggregation(AggregationBuilders.terms(NodeComponentTable.COLUMN_PEER_ID).field(NodeComponentTable.COLUMN_PEER_ID).size(100)));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        Terms componentIdTerms = searchResponse.getAggregations().get(NodeComponentTable.COLUMN_COMPONENT_ID);
        JsonArray nodeComponentArray = new JsonArray();
        for (Terms.Bucket componentIdBucket : componentIdTerms.getBuckets()) {
            int componentId = componentIdBucket.getKeyAsNumber().intValue();
            buildComponentArray(componentIdBucket, componentId, nodeComponentArray);
        }

        return nodeComponentArray;
    }

    private void buildComponentArray(Terms.Bucket componentBucket, int componentId, JsonArray nodeComponentArray) {
        Terms peerIdTerms = componentBucket.getAggregations().get(NodeComponentTable.COLUMN_PEER_ID);
        for (Terms.Bucket peerIdBucket : peerIdTerms.getBuckets()) {
            int peerId = peerIdBucket.getKeyAsNumber().intValue();

            JsonObject nodeComponentObj = new JsonObject();
            nodeComponentObj.addProperty(NodeComponentTable.COLUMN_COMPONENT_ID, componentId);
            nodeComponentObj.addProperty(NodeComponentTable.COLUMN_PEER_ID, peerId);
            nodeComponentArray.add(nodeComponentObj);
        }
    }
}
