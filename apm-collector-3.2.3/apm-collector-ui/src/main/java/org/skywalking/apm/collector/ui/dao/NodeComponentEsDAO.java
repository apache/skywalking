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

package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.skywalking.apm.collector.cache.ApplicationCache;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.define.node.NodeComponentTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NodeComponentEsDAO extends EsDAO implements INodeComponentDAO {

    private final Logger logger = LoggerFactory.getLogger(NodeComponentEsDAO.class);

    @Override public JsonArray load(long startTime, long endTime) {
        logger.debug("node component load, start time: {}, end time: {}", startTime, endTime);
        JsonArray nodeComponentArray = new JsonArray();
        nodeComponentArray.addAll(aggregationByComponentId(startTime, endTime));
        nodeComponentArray.addAll(aggregationByComponentName(startTime, endTime));
        return nodeComponentArray;
    }

    private JsonArray aggregationByComponentId(long startTime, long endTime) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(NodeComponentTable.TABLE);
        searchRequestBuilder.setTypes(NodeComponentTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeComponentTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(AggregationBuilders.terms(NodeComponentTable.COLUMN_COMPONENT_ID).field(NodeComponentTable.COLUMN_COMPONENT_ID).size(100)
            .subAggregation(AggregationBuilders.terms(NodeComponentTable.COLUMN_PEER).field(NodeComponentTable.COLUMN_PEER).size(100))
            .subAggregation(AggregationBuilders.terms(NodeComponentTable.COLUMN_PEER_ID).field(NodeComponentTable.COLUMN_PEER_ID).size(100)));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        Terms componentIdTerms = searchResponse.getAggregations().get(NodeComponentTable.COLUMN_COMPONENT_ID);
        JsonArray nodeComponentArray = new JsonArray();
        for (Terms.Bucket componentIdBucket : componentIdTerms.getBuckets()) {
            int componentId = componentIdBucket.getKeyAsNumber().intValue();
            String componentName = ComponentsDefine.getInstance().getComponentName(componentId);
            if (componentId != 0) {
                buildComponentArray(componentIdBucket, componentName, nodeComponentArray);
            }
        }

        return nodeComponentArray;
    }

    private JsonArray aggregationByComponentName(long startTime, long endTime) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(NodeComponentTable.TABLE);
        searchRequestBuilder.setTypes(NodeComponentTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeComponentTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(AggregationBuilders.terms(NodeComponentTable.COLUMN_COMPONENT_NAME).field(NodeComponentTable.COLUMN_COMPONENT_NAME).size(100)
            .subAggregation(AggregationBuilders.terms(NodeComponentTable.COLUMN_PEER).field(NodeComponentTable.COLUMN_PEER).size(100))
            .subAggregation(AggregationBuilders.terms(NodeComponentTable.COLUMN_PEER_ID).field(NodeComponentTable.COLUMN_PEER_ID).size(100)));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        Terms componentNameTerms = searchResponse.getAggregations().get(NodeComponentTable.COLUMN_COMPONENT_NAME);
        JsonArray nodeComponentArray = new JsonArray();
        for (Terms.Bucket componentNameBucket : componentNameTerms.getBuckets()) {
            String componentName = componentNameBucket.getKeyAsString();
            if (StringUtils.isNotEmpty(componentName)) {
                buildComponentArray(componentNameBucket, componentName, nodeComponentArray);
            }
        }

        return nodeComponentArray;
    }

    private void buildComponentArray(Terms.Bucket componentBucket, String componentName, JsonArray nodeComponentArray) {
        Terms peerIdTerms = componentBucket.getAggregations().get(NodeComponentTable.COLUMN_PEER_ID);
        for (Terms.Bucket peerIdBucket : peerIdTerms.getBuckets()) {
            int peerId = peerIdBucket.getKeyAsNumber().intValue();

            if (peerId != 0) {
                String peer = ApplicationCache.get(peerId);

                JsonObject nodeComponentObj = new JsonObject();
                nodeComponentObj.addProperty("componentName", componentName);
                nodeComponentObj.addProperty("peer", peer);
                nodeComponentArray.add(nodeComponentObj);
            }
        }

        Terms peerTerms = componentBucket.getAggregations().get(NodeComponentTable.COLUMN_PEER);
        for (Terms.Bucket peerBucket : peerTerms.getBuckets()) {
            String peer = peerBucket.getKeyAsString();

            if (StringUtils.isNotEmpty(peer)) {
                JsonObject nodeComponentObj = new JsonObject();
                nodeComponentObj.addProperty("componentName", componentName);
                nodeComponentObj.addProperty("peer", peer);
                nodeComponentArray.add(nodeComponentObj);
            }
        }
    }
}
