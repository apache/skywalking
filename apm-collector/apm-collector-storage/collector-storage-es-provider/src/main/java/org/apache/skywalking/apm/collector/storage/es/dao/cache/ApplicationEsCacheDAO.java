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

package org.apache.skywalking.apm.collector.storage.es.dao.cache;

import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.storage.dao.cache.IApplicationCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.storage.table.register.ApplicationTable;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationEsCacheDAO extends EsDAO implements IApplicationCacheDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationEsCacheDAO.class);

    public ApplicationEsCacheDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getApplicationIdByCode(String applicationCode) {
        ElasticSearchClient client = getClient();

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(ApplicationTable.TABLE);
        searchRequestBuilder.setTypes(ApplicationTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ApplicationTable.COLUMN_APPLICATION_CODE, applicationCode));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ApplicationTable.COLUMN_IS_ADDRESS, BooleanUtils.FALSE));

        searchRequestBuilder.setQuery(boolQueryBuilder);
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            return (int)searchHit.getSource().get(ApplicationTable.COLUMN_APPLICATION_ID);
        }
        return 0;
    }

    @Override public Application getApplication(int applicationId) {
        logger.debug("get application code, applicationId: {}", applicationId);
        ElasticSearchClient client = getClient();
        GetRequestBuilder getRequestBuilder = client.prepareGet(ApplicationTable.TABLE, String.valueOf(applicationId));

        GetResponse getResponse = getRequestBuilder.get();
        if (getResponse.isExists()) {
            Application application = new Application();
            application.setApplicationId(applicationId);
            application.setApplicationCode((String)getResponse.getSource().get(ApplicationTable.COLUMN_APPLICATION_CODE));
            application.setIsAddress(((Number)getResponse.getSource().get(ApplicationTable.COLUMN_IS_ADDRESS)).intValue());
            return application;
        }
        return null;
    }

    @Override public int getApplicationIdByAddressId(int addressId) {
        ElasticSearchClient client = getClient();

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(ApplicationTable.TABLE);
        searchRequestBuilder.setTypes(ApplicationTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ApplicationTable.COLUMN_ADDRESS_ID, addressId));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ApplicationTable.COLUMN_IS_ADDRESS, BooleanUtils.TRUE));

        searchRequestBuilder.setQuery(boolQueryBuilder);
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            return (int)searchHit.getSource().get(ApplicationTable.COLUMN_APPLICATION_ID);
        }
        return 0;
    }
}
