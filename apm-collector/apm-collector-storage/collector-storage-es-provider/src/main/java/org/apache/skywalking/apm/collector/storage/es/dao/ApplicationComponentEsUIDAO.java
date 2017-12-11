/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package org.apache.skywalking.apm.collector.storage.es.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationComponentUIDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationComponentEsUIDAO extends EsDAO implements IApplicationComponentUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationComponentEsPersistenceDAO.class);

    public ApplicationComponentEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public JsonArray load(long startTime, long endTime) {
        logger.debug("application component load, start time: {}, end time: {}", startTime, endTime);
        JsonArray applicationComponentArray = new JsonArray();
        applicationComponentArray.addAll(aggregationByComponentId(startTime, endTime));
        return applicationComponentArray;
    }

    private JsonArray aggregationByComponentId(long startTime, long endTime) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ApplicationComponentTable.TABLE);
        searchRequestBuilder.setTypes(ApplicationComponentTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(ApplicationComponentTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(AggregationBuilders.terms(ApplicationComponentTable.COLUMN_COMPONENT_ID).field(ApplicationComponentTable.COLUMN_COMPONENT_ID).size(100)
            .subAggregation(AggregationBuilders.terms(ApplicationComponentTable.COLUMN_PEER_ID).field(ApplicationComponentTable.COLUMN_PEER_ID).size(100)));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        Terms componentIdTerms = searchResponse.getAggregations().get(ApplicationComponentTable.COLUMN_COMPONENT_ID);
        JsonArray applicationComponentArray = new JsonArray();
        for (Terms.Bucket componentIdBucket : componentIdTerms.getBuckets()) {
            int componentId = componentIdBucket.getKeyAsNumber().intValue();
            buildComponentArray(componentIdBucket, componentId, applicationComponentArray);
        }

        return applicationComponentArray;
    }

    private void buildComponentArray(Terms.Bucket componentBucket, int componentId,
        JsonArray applicationComponentArray) {
        Terms peerIdTerms = componentBucket.getAggregations().get(ApplicationComponentTable.COLUMN_PEER_ID);
        for (Terms.Bucket peerIdBucket : peerIdTerms.getBuckets()) {
            int peerId = peerIdBucket.getKeyAsNumber().intValue();

            JsonObject applicationComponentObj = new JsonObject();
            applicationComponentObj.addProperty(ApplicationComponentTable.COLUMN_COMPONENT_ID, componentId);
            applicationComponentObj.addProperty(ApplicationComponentTable.COLUMN_PEER_ID, peerId);
            applicationComponentArray.add(applicationComponentObj);
        }
    }
}
