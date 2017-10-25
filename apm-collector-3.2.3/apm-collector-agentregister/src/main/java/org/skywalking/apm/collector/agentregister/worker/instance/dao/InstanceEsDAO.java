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

package org.skywalking.apm.collector.agentregister.worker.instance.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;
import org.skywalking.apm.collector.storage.define.register.InstanceTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceEsDAO extends EsDAO implements IInstanceDAO {

    private final Logger logger = LoggerFactory.getLogger(InstanceEsDAO.class);

    @Override public int getInstanceId(int applicationId, String agentUUID) {
        ElasticSearchClient client = getClient();

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes("type");
        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
        BoolQueryBuilder builder = QueryBuilders.boolQuery();
        builder.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_APPLICATION_ID, applicationId));
        builder.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_AGENT_UUID, agentUUID));
        searchRequestBuilder.setQuery(builder);
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            return (int)searchHit.getSource().get(InstanceTable.COLUMN_INSTANCE_ID);
        }
        return 0;
    }

    @Override public int getMaxInstanceId() {
        return getMaxId(InstanceTable.TABLE, InstanceTable.COLUMN_INSTANCE_ID);
    }

    @Override public int getMinInstanceId() {
        return getMinId(InstanceTable.TABLE, InstanceTable.COLUMN_INSTANCE_ID);
    }

    @Override public void save(InstanceDataDefine.Instance instance) {
        logger.debug("save instance register info, application id: {}, agentUUID: {}", instance.getApplicationId(), instance.getAgentUUID());
        ElasticSearchClient client = getClient();
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceTable.COLUMN_INSTANCE_ID, instance.getInstanceId());
        source.put(InstanceTable.COLUMN_APPLICATION_ID, instance.getApplicationId());
        source.put(InstanceTable.COLUMN_AGENT_UUID, instance.getAgentUUID());
        source.put(InstanceTable.COLUMN_REGISTER_TIME, instance.getRegisterTime());
        source.put(InstanceTable.COLUMN_HEARTBEAT_TIME, instance.getHeartBeatTime());
        source.put(InstanceTable.COLUMN_OS_INFO, instance.getOsInfo());

        IndexResponse response = client.prepareIndex(InstanceTable.TABLE, instance.getId()).setSource(source).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
        logger.debug("save instance register info, application id: {}, agentUUID: {}, status: {}", instance.getApplicationId(), instance.getAgentUUID(), response.status().name());
    }

    @Override public void updateHeartbeatTime(int instanceId, long heartbeatTime) {
        ElasticSearchClient client = getClient();
        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.index(InstanceTable.TABLE);
        updateRequest.type("type");
        updateRequest.id(String.valueOf(instanceId));
        updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        Map<String, Object> source = new HashMap<>();
        source.put(InstanceTable.COLUMN_HEARTBEAT_TIME, heartbeatTime);

        updateRequest.doc(source);
        client.update(updateRequest);
    }
}
