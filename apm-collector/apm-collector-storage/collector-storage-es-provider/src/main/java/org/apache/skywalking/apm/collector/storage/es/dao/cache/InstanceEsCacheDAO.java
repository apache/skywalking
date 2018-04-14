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
import org.apache.skywalking.apm.collector.storage.dao.cache.IInstanceCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
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
public class InstanceEsCacheDAO extends EsDAO implements IInstanceCacheDAO {

    public InstanceEsCacheDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getApplicationId(int instanceId) {
        GetResponse response = getClient().prepareGet(InstanceTable.TABLE, String.valueOf(instanceId)).get();
        if (response.isExists()) {
            return (int)response.getSource().get(InstanceTable.APPLICATION_ID.getName());
        } else {
            return 0;
        }
    }

    @Override public int getInstanceIdByAgentUUID(int applicationId, String agentUUID) {
        ElasticSearchClient client = getClient();

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
        BoolQueryBuilder builder = QueryBuilders.boolQuery();
        builder.must().add(QueryBuilders.termQuery(InstanceTable.APPLICATION_ID.getName(), applicationId));
        builder.must().add(QueryBuilders.termQuery(InstanceTable.AGENT_UUID.getName(), agentUUID));
        builder.must().add(QueryBuilders.termQuery(InstanceTable.IS_ADDRESS.getName(), BooleanUtils.FALSE));
        searchRequestBuilder.setQuery(builder);
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            return (int)searchHit.getSource().get(InstanceTable.INSTANCE_ID.getName());
        }
        return 0;
    }

    @Override public int getInstanceIdByAddressId(int applicationId, int addressId) {
        ElasticSearchClient client = getClient();

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
        BoolQueryBuilder builder = QueryBuilders.boolQuery();
        builder.must().add(QueryBuilders.termQuery(InstanceTable.APPLICATION_ID.getName(), applicationId));
        builder.must().add(QueryBuilders.termQuery(InstanceTable.ADDRESS_ID.getName(), addressId));
        builder.must().add(QueryBuilders.termQuery(InstanceTable.IS_ADDRESS.getName(), BooleanUtils.TRUE));
        searchRequestBuilder.setQuery(builder);
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            return (int)searchHit.getSource().get(InstanceTable.INSTANCE_ID.getName());
        }
        return 0;
    }
}
