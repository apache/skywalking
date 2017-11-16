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
import org.skywalking.apm.collector.storage.dao.INodeMappingUIDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.node.NodeMappingTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NodeMappingEsUIDAO extends EsDAO implements INodeMappingUIDAO {

    private final Logger logger = LoggerFactory.getLogger(NodeMappingEsUIDAO.class);

    public NodeMappingEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public JsonArray load(long startTime, long endTime) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(NodeMappingTable.TABLE);
        searchRequestBuilder.setTypes(NodeMappingTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeMappingTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(
            AggregationBuilders.terms(NodeMappingTable.COLUMN_APPLICATION_ID).field(NodeMappingTable.COLUMN_APPLICATION_ID).size(100)
                .subAggregation(AggregationBuilders.terms(NodeMappingTable.COLUMN_ADDRESS_ID).field(NodeMappingTable.COLUMN_ADDRESS_ID).size(100)));
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        Terms applicationIdTerms = searchResponse.getAggregations().get(NodeMappingTable.COLUMN_APPLICATION_ID);

        JsonArray nodeMappingArray = new JsonArray();
        for (Terms.Bucket applicationIdBucket : applicationIdTerms.getBuckets()) {
            int applicationId = applicationIdBucket.getKeyAsNumber().intValue();
            Terms addressIdTerms = applicationIdBucket.getAggregations().get(NodeMappingTable.COLUMN_ADDRESS_ID);
            for (Terms.Bucket addressIdBucket : addressIdTerms.getBuckets()) {
                int addressId = addressIdBucket.getKeyAsNumber().intValue();
                JsonObject nodeMappingObj = new JsonObject();
                nodeMappingObj.addProperty(NodeMappingTable.COLUMN_APPLICATION_ID, applicationId);
                nodeMappingObj.addProperty(NodeMappingTable.COLUMN_ADDRESS_ID, addressId);
                nodeMappingArray.add(nodeMappingObj);
            }
        }
        logger.debug("node mapping data: {}", nodeMappingArray.toString());
        return nodeMappingArray;
    }
}
