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

package org.apache.skywalking.apm.collector.storage.es.http.dao.register;

import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.register.IInstanceRegisterDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.core.Update;

/**
 * @author peng-yongsheng
 */
public class InstanceRegisterEsDAO extends EsHttpDAO implements IInstanceRegisterDAO {

    private final Logger logger = LoggerFactory.getLogger(InstanceRegisterEsDAO.class);

    public InstanceRegisterEsDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public int getMaxInstanceId() {
        return getMaxId(InstanceTable.TABLE, InstanceTable.COLUMN_INSTANCE_ID);
    }

    @Override public int getMinInstanceId() {
        return getMinId(InstanceTable.TABLE, InstanceTable.COLUMN_INSTANCE_ID);
    }

    @Override public void save(Instance instance) {
        logger.debug("save instance register info, application getId: {}, agentUUID: {}", instance.getApplicationId(), instance.getAgentUUID());
        ElasticSearchHttpClient client = getClient();
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceTable.COLUMN_INSTANCE_ID, instance.getInstanceId());
        source.put(InstanceTable.COLUMN_APPLICATION_ID, instance.getApplicationId());
        source.put(InstanceTable.COLUMN_AGENT_UUID, instance.getAgentUUID());
        source.put(InstanceTable.COLUMN_REGISTER_TIME, TimeBucketUtils.INSTANCE.getSecondTimeBucket(instance.getRegisterTime()));
        source.put(InstanceTable.COLUMN_HEARTBEAT_TIME, TimeBucketUtils.INSTANCE.getSecondTimeBucket(instance.getHeartBeatTime()));
        source.put(InstanceTable.COLUMN_OS_INFO, instance.getOsInfo());
        source.put(InstanceTable.COLUMN_ADDRESS_ID, instance.getAddressId());
        source.put(InstanceTable.COLUMN_IS_ADDRESS, instance.getIsAddress());

        boolean response = client.prepareIndex(InstanceTable.TABLE, instance.getId(),source,true);
        logger.debug("save instance register info, application getId: {}, agentUUID: {}, status: {}", instance.getApplicationId(), instance.getAgentUUID(), response);
    }

    @Override public void updateHeartbeatTime(int instanceId, long heartbeatTime) {
        ElasticSearchHttpClient client = getClient();

        Map<String, Object> source = new HashMap<>();
        source.put(InstanceTable.COLUMN_HEARTBEAT_TIME, TimeBucketUtils.INSTANCE.getSecondTimeBucket(heartbeatTime));

        client.prepareUpdate(InstanceTable.TABLE, String.valueOf(instanceId), source);
    }
}
