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
import org.elasticsearch.action.update.UpdateRequestBuilder;
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

    @Override
    public int getMaxInstanceId() {
        return getMaxId(InstanceTable.TABLE, InstanceTable.INSTANCE_ID.getName());
    }

    @Override
    public int getMinInstanceId() {
        return getMinId(InstanceTable.TABLE, InstanceTable.INSTANCE_ID.getName());
    }

    @Override
    public void save(Instance instance) {
        logger.debug("save instance register info, application getApplicationId: {}, agentUUID: {}", instance.getApplicationId(), instance.getAgentUUID());
        ElasticSearchClient client = getClient();
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceTable.INSTANCE_ID.getName(), instance.getInstanceId());
        target.put(InstanceTable.APPLICATION_ID.getName(), instance.getApplicationId());
        target.put(InstanceTable.APPLICATION_CODE.getName(), instance.getApplicationCode());
        target.put(InstanceTable.AGENT_UUID.getName(), instance.getAgentUUID());
        target.put(InstanceTable.REGISTER_TIME.getName(), TimeBucketUtils.INSTANCE.getSecondTimeBucket(instance.getRegisterTime()));
        target.put(InstanceTable.HEARTBEAT_TIME.getName(), TimeBucketUtils.INSTANCE.getSecondTimeBucket(instance.getHeartBeatTime()));
        target.put(InstanceTable.OS_INFO.getName(), instance.getOsInfo());
        target.put(InstanceTable.ADDRESS_ID.getName(), instance.getAddressId());
        target.put(InstanceTable.IS_ADDRESS.getName(), instance.getIsAddress());

        IndexResponse response = client.prepareIndex(InstanceTable.TABLE, instance.getId()).setSource(target).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
        logger.debug("save instance register info, application getApplicationId: {}, agentUUID: {}, status: {}", instance.getApplicationId(), instance.getAgentUUID(), response.status().name());
    }

    @Override
    public void updateHeartbeatTime(int instanceId, long heartbeatTime) {
        UpdateRequestBuilder updateRequestBuilder = getClient().prepareUpdate(InstanceTable.TABLE, String.valueOf(instanceId));
        updateRequestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceTable.HEARTBEAT_TIME.getName(), TimeBucketUtils.INSTANCE.getSecondTimeBucket(heartbeatTime));
        updateRequestBuilder.setDoc(target);

        updateRequestBuilder.get();
    }
}
