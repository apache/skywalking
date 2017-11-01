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
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.storage.dao.IInstanceDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.register.InstanceDataDefine;
import org.skywalking.apm.collector.storage.table.register.InstanceTable;
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

    @Override public void save(Data data) {
        String id = InstanceDataDefine.Instance.INSTANCE.getId(data);
        int instanceId = InstanceDataDefine.Instance.INSTANCE.getInstanceId(data);
        int applicationId = InstanceDataDefine.Instance.INSTANCE.getApplicationId(data);
        String agentUUID = InstanceDataDefine.Instance.INSTANCE.getAgentUUID(data);
        long registerTime = InstanceDataDefine.Instance.INSTANCE.getRegisterTime(data);
        long heartBeatTime = InstanceDataDefine.Instance.INSTANCE.getHeartBeatTime(data);
        String osInfo = InstanceDataDefine.Instance.INSTANCE.getOsInfo(data);

        logger.debug("save instance register info, application id: {}, agentUUID: {}", applicationId, agentUUID);
        ElasticSearchClient client = getClient();
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceTable.COLUMN_INSTANCE_ID, instanceId);
        source.put(InstanceTable.COLUMN_APPLICATION_ID, applicationId);
        source.put(InstanceTable.COLUMN_AGENT_UUID, agentUUID);
        source.put(InstanceTable.COLUMN_REGISTER_TIME, registerTime);
        source.put(InstanceTable.COLUMN_HEARTBEAT_TIME, heartBeatTime);
        source.put(InstanceTable.COLUMN_OS_INFO, osInfo);

        IndexResponse response = client.prepareIndex(InstanceTable.TABLE, id).setSource(source).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
        logger.debug("save instance register info, application id: {}, agentUUID: {}, status: {}", applicationId, agentUUID, response.status().name());
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
