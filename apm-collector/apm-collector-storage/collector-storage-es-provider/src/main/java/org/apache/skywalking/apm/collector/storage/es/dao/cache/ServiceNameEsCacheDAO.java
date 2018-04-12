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
import org.apache.skywalking.apm.collector.storage.dao.cache.IServiceNameCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

/**
 * @author peng-yongsheng
 */
public class ServiceNameEsCacheDAO extends EsDAO implements IServiceNameCacheDAO {

    public ServiceNameEsCacheDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public ServiceName get(int serviceId) {
        GetRequestBuilder getRequestBuilder = getClient().prepareGet(ServiceNameTable.TABLE, String.valueOf(serviceId));

        GetResponse getResponse = getRequestBuilder.get();

        if (getResponse.isExists()) {
            ServiceName serviceName = new ServiceName();
            serviceName.setApplicationId(((Number)getResponse.getSource().get(ServiceNameTable.APPLICATION_ID.getName())).intValue());
            serviceName.setServiceId(serviceId);
            serviceName.setServiceName((String)getResponse.getSource().get(ServiceNameTable.SERVICE_NAME.getName()));
            return serviceName;
        }
        return null;
    }

    @Override public int getServiceId(int applicationId, int srcSpanType, String serviceName) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceNameTable.TABLE);
        searchRequestBuilder.setTypes(ServiceNameTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.termQuery(ServiceNameTable.APPLICATION_ID.getName(), applicationId));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceNameTable.SRC_SPAN_TYPE.getName(), srcSpanType));
        boolQuery.must().add(QueryBuilders.termQuery(ServiceNameTable.SERVICE_NAME_KEYWORD.getName(), serviceName));
        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.get();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            return (int)searchHit.getSource().get(ServiceNameTable.SERVICE_ID.getName());
        }
        return 0;
    }
}
