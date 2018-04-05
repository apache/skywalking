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

package org.apache.skywalking.apm.collector.storage.es.http.dao.cache;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.storage.dao.cache.IApplicationCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.storage.table.register.ApplicationTable;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import io.searchbox.core.DocumentResult;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;

/**
 * @author cyberdak
 */
public class ApplicationEsCacheDAO extends EsHttpDAO implements IApplicationCacheDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationEsCacheDAO.class);

    public ApplicationEsCacheDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public int getApplicationIdByCode(String applicationCode) {
        ElasticSearchHttpClient client = getClient();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ApplicationTable.COLUMN_APPLICATION_CODE, applicationCode));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ApplicationTable.COLUMN_IS_ADDRESS, BooleanUtils.FALSE));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);
        searchSourceBuilder.size(1);
        
        
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(ApplicationTable.TABLE).build();
        
        SearchResult result =  getClient().execute(search);

        if (result.getTotal() > 0) {
            JsonObject searchHit = result.getJsonObject().getAsJsonObject("hits").getAsJsonArray("hits").get(0).getAsJsonObject();
            return searchHit.get("_source").getAsJsonObject().get(ApplicationTable.COLUMN_APPLICATION_ID).getAsInt();
        }
        return 0;
    }

    @Override public Application getApplication(int applicationId) {
        logger.debug("get application code, applicationId: {}", applicationId);
        ElasticSearchHttpClient client = getClient();
        DocumentResult getResponse = client.prepareGet(ApplicationTable.TABLE, String.valueOf(applicationId));

        if (getResponse.isSucceeded()) {
            JsonObject source = getResponse.getJsonObject().getAsJsonObject("_source");
            Application application = new Application();
            application.setApplicationId(applicationId);
            application.setApplicationCode(source.get(ApplicationTable.COLUMN_APPLICATION_CODE).getAsString());
            application.setIsAddress((source.get(ApplicationTable.COLUMN_IS_ADDRESS)).getAsInt());
            return application;
        }
        return null;
    }

    @Override public int getApplicationIdByAddressId(int addressId) {
        ElasticSearchHttpClient client = getClient();

//        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(ApplicationTable.TABLE);
//        searchRequestBuilder.setTypes("type");
//        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ApplicationTable.COLUMN_ADDRESS_ID, addressId));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ApplicationTable.COLUMN_IS_ADDRESS, BooleanUtils.TRUE));
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);
        searchSourceBuilder.size(1);
        
        Search search = new Search.Builder(searchSourceBuilder.toString()) .addIndex(ApplicationTable.TABLE).build();

        SearchResult result = client.execute(search);
        
        if (result.getTotal() > 0) {
            JsonObject searchHit = result.getJsonObject().getAsJsonObject("hits").getAsJsonArray("hits").get(0).getAsJsonObject().getAsJsonObject("_source");
            return (int)searchHit.get(ApplicationTable.COLUMN_APPLICATION_ID).getAsInt();
        }
        return 0;
    }
}
