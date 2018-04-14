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

package org.apache.skywalking.apm.collector.storage.h2.dao.cache;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.cache.IInstanceCacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceH2CacheDAO extends H2DAO implements IInstanceCacheDAO {

    private static final Logger logger = LoggerFactory.getLogger(InstanceH2CacheDAO.class);

    private static final String GET_APPLICATION_ID_SQL = "select {0} from {1} where {2} = ?";
    private static final String GET_INSTANCE_ID_SQL = "select {0} from {1} where {2} = ? and {3} = ? and {4} = ?";

    public InstanceH2CacheDAO(H2Client client) {
        super(client);
    }

    @Override public int getApplicationId(int instanceId) {
        logger.info("get the application id by instance id = {}", instanceId);
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_APPLICATION_ID_SQL, InstanceTable.APPLICATION_ID.getName(), InstanceTable.TABLE, InstanceTable.INSTANCE_ID.getName());
        Object[] params = new Object[] {instanceId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getInt(InstanceTable.APPLICATION_ID.getName());
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    @Override public int getInstanceIdByAgentUUID(int applicationId, String agentUUID) {
        logger.info("get the instance id by application id = {}, agentUUID = {}", applicationId, agentUUID);
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_INSTANCE_ID_SQL, InstanceTable.INSTANCE_ID.getName(), InstanceTable.TABLE, InstanceTable.APPLICATION_ID.getName(),
            InstanceTable.AGENT_UUID.getName(), InstanceTable.IS_ADDRESS.getName());
        Object[] params = new Object[] {applicationId, agentUUID, BooleanUtils.FALSE};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getInt(InstanceTable.INSTANCE_ID.getName());
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    @Override public int getInstanceIdByAddressId(int applicationId, int addressId) {
        logger.info("get the instance id by application id = {}, address id = {}", applicationId, addressId);
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_INSTANCE_ID_SQL, InstanceTable.INSTANCE_ID.getName(), InstanceTable.TABLE, InstanceTable.APPLICATION_ID.getName(),
            InstanceTable.ADDRESS_ID.getName(), InstanceTable.IS_ADDRESS.getName());
        Object[] params = new Object[] {applicationId, addressId, BooleanUtils.TRUE};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getInt(InstanceTable.INSTANCE_ID.getName());
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }
}
