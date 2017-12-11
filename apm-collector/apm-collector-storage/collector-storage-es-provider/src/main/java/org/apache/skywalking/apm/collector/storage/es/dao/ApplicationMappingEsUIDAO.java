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
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMappingTable;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationMappingUIDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationMappingEsUIDAO extends EsDAO implements IApplicationMappingUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationMappingEsUIDAO.class);

    public ApplicationMappingEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public JsonArray load(long startTime, long endTime) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ApplicationMappingTable.TABLE);
        searchRequestBuilder.setTypes(ApplicationMappingTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(ApplicationMappingTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(
            AggregationBuilders.terms(ApplicationMappingTable.COLUMN_APPLICATION_ID).field(ApplicationMappingTable.COLUMN_APPLICATION_ID).size(100)
                .subAggregation(AggregationBuilders.terms(ApplicationMappingTable.COLUMN_ADDRESS_ID).field(ApplicationMappingTable.COLUMN_ADDRESS_ID).size(100)));
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        Terms applicationIdTerms = searchResponse.getAggregations().get(ApplicationMappingTable.COLUMN_APPLICATION_ID);

        JsonArray applicationMappingArray = new JsonArray();
        for (Terms.Bucket applicationIdBucket : applicationIdTerms.getBuckets()) {
            int applicationId = applicationIdBucket.getKeyAsNumber().intValue();
            Terms addressIdTerms = applicationIdBucket.getAggregations().get(ApplicationMappingTable.COLUMN_ADDRESS_ID);
            for (Terms.Bucket addressIdBucket : addressIdTerms.getBuckets()) {
                int addressId = addressIdBucket.getKeyAsNumber().intValue();
                JsonObject applicationMappingObj = new JsonObject();
                applicationMappingObj.addProperty(ApplicationMappingTable.COLUMN_APPLICATION_ID, applicationId);
                applicationMappingObj.addProperty(ApplicationMappingTable.COLUMN_ADDRESS_ID, addressId);
                applicationMappingArray.add(applicationMappingObj);
            }
        }
        logger.debug("application mapping data: {}", applicationMappingArray.toString());
        return applicationMappingArray;
    }
}
