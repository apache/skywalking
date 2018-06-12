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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.register;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.register.IInstanceRegisterDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class InstanceRegisterShardingjdbcDAO extends ShardingjdbcDAO implements IInstanceRegisterDAO {

    private static final Logger logger = LoggerFactory.getLogger(InstanceRegisterShardingjdbcDAO.class);

    public InstanceRegisterShardingjdbcDAO(ShardingjdbcClient client) {
        super(client);
    }

    private static final String UPDATE_HEARTBEAT_TIME_SQL = "update {0} set {1} = ? where {2} = ?";

    @Override public int getMaxInstanceId() {
        return getMaxId(InstanceTable.TABLE, InstanceTable.INSTANCE_ID.getName());
    }

    @Override public int getMinInstanceId() {
        return getMinId(InstanceTable.TABLE, InstanceTable.INSTANCE_ID.getName());
    }

    @Override public void save(Instance instance) {
        ShardingjdbcClient client = getClient();
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceTable.ID.getName(), instance.getId());
        target.put(InstanceTable.INSTANCE_ID.getName(), instance.getInstanceId());
        target.put(InstanceTable.APPLICATION_ID.getName(), instance.getApplicationId());
        target.put(InstanceTable.APPLICATION_CODE.getName(), instance.getApplicationCode());
        target.put(InstanceTable.AGENT_UUID.getName(), instance.getAgentUUID());
        target.put(InstanceTable.REGISTER_TIME.getName(), TimeBucketUtils.INSTANCE.getSecondTimeBucket(instance.getRegisterTime()));
        target.put(InstanceTable.HEARTBEAT_TIME.getName(), TimeBucketUtils.INSTANCE.getSecondTimeBucket(instance.getHeartBeatTime()));
        target.put(InstanceTable.OS_INFO.getName(), instance.getOsInfo());
        target.put(InstanceTable.ADDRESS_ID.getName(), instance.getAddressId());
        target.put(InstanceTable.IS_ADDRESS.getName(), instance.getIsAddress());

        String sql = SqlBuilder.buildBatchInsertSql(InstanceTable.TABLE, target.keySet());
        Object[] params = target.values().toArray(new Object[0]);
        try {
            client.execute(sql, params);
        } catch (ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public void updateHeartbeatTime(int instanceId, long heartbeatTime) {
        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(UPDATE_HEARTBEAT_TIME_SQL, InstanceTable.TABLE, InstanceTable.HEARTBEAT_TIME.getName(),
            InstanceTable.ID.getName());
        Object[] params = new Object[] {TimeBucketUtils.INSTANCE.getSecondTimeBucket(heartbeatTime), instanceId};
        try {
            client.execute(sql, params);
        } catch (ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
