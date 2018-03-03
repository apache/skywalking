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

package org.apache.skywalking.apm.collector.storage.es.dao.register;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.register.IInstanceRegisterDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceRegisterEsDAO extends EsDAO implements IInstanceRegisterDAO {

    private final Logger logger = LoggerFactory.getLogger(InstanceRegisterEsDAO.class);

    public InstanceRegisterEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getMaxInstanceId() {
        return getMaxId(InstanceTable.TABLE, InstanceTable.COLUMN_INSTANCE_ID);
    }

    @Override public int getMinInstanceId() {
        return getMinId(InstanceTable.TABLE, InstanceTable.COLUMN_INSTANCE_ID);
    }

    @Override public void save(Instance instance) {
        logger.debug("save instance register info, application getApplicationId: {}, agentUUID: {}", instance.getApplicationId(), instance.getAgentUUID());
        ElasticSearchClient client = getClient();
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceTable.COLUMN_INSTANCE_ID, instance.getInstanceId());
        source.put(InstanceTable.COLUMN_APPLICATION_ID, instance.getApplicationId());
        source.put(InstanceTable.COLUMN_APPLICATION_CODE, instance.getApplicationCode());
        source.put(InstanceTable.COLUMN_AGENT_UUID, instance.getAgentUUID());
        source.put(InstanceTable.COLUMN_REGISTER_TIME, TimeBucketUtils.INSTANCE.getSecondTimeBucket(instance.getRegisterTime()));
        source.put(InstanceTable.COLUMN_HEARTBEAT_TIME, TimeBucketUtils.INSTANCE.getSecondTimeBucket(instance.getHeartBeatTime()));
        source.put(InstanceTable.COLUMN_OS_INFO, instance.getOsInfo());
        source.put(InstanceTable.COLUMN_ADDRESS_ID, instance.getAddressId());
        source.put(InstanceTable.COLUMN_IS_ADDRESS, instance.getIsAddress());

        IndexResponse response = client.prepareIndex(InstanceTable.TABLE, instance.getId()).setSource(source).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
        logger.debug("save instance register info, application getApplicationId: {}, agentUUID: {}, status: {}", instance.getApplicationId(), instance.getAgentUUID(), response.status().name());
    }

    @Override public void updateHeartbeatTime(int instanceId, long heartbeatTime) {
        ElasticSearchClient client = getClient();
        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.index(InstanceTable.TABLE);
        updateRequest.type(InstanceTable.TABLE_TYPE);
        updateRequest.id(String.valueOf(instanceId));
        updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        Map<String, Object> source = new HashMap<>();
        source.put(InstanceTable.COLUMN_HEARTBEAT_TIME, TimeBucketUtils.INSTANCE.getSecondTimeBucket(heartbeatTime));

        updateRequest.doc(source);
        client.update(updateRequest);
    }
}
