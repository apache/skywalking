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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.ui;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.apache.skywalking.apm.collector.storage.ui.application.Application;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class InstanceShardingjdbcUIDAO extends ShardingjdbcDAO implements IInstanceUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(InstanceShardingjdbcUIDAO.class);

    public InstanceShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    private static final String GET_INSTANCE_SQL = "select * from {0} where {1} = ?";
    private static final String GET_APPLICATIONS_SQL = "select {4}, count({0}) as cnt from {1} where {2} >= ? and {3} <= ? and {4} in (?) group by {4} limit 100";
    private static final String GET_REGISTER_TIME_SQL = "select {2} from {0} where {1} = ? order by {2} asc";
    private static final String GET_HEARTBEAT_TIME_SQL = "select {2} from {0} where {1} = ? order by {2} desc";

    @Override
    public List<Application> getApplications(long startSecondTimeBucket, long endSecondTimeBucket,
        int... applicationIds) {
        ShardingjdbcClient client = getClient();
        List<Application> applications = new LinkedList<>();
        String sqlOne = SqlBuilder.buildSql(GET_APPLICATIONS_SQL, InstanceTable.INSTANCE_ID.getName(),
            InstanceTable.TABLE, InstanceTable.HEARTBEAT_TIME.getName(), InstanceTable.REGISTER_TIME.getName(), InstanceTable.APPLICATION_ID.getName());
        String sqlTwo = SqlBuilder.buildSql(GET_APPLICATIONS_SQL, InstanceTable.INSTANCE_ID.getName(),
                InstanceTable.TABLE, InstanceTable.REGISTER_TIME.getName(), InstanceTable.HEARTBEAT_TIME.getName(), InstanceTable.APPLICATION_ID.getName());
        String applicationIdsParam = Arrays.toString(applicationIds).replace("[", "").replace("]", "");
        Object[] params = new Object[] {startSecondTimeBucket, endSecondTimeBucket, applicationIdsParam};
        try (
                ResultSet rs = client.executeQuery(sqlOne, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                Integer applicationId = rs.getInt(InstanceTable.APPLICATION_ID.getName());
                logger.debug("applicationId: {}", applicationId);
                Application application = new Application();
                application.setId(applicationId);
                application.setNumOfServer(rs.getInt("cnt"));
                applications.add(application);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        try (
                ResultSet rs = client.executeQuery(sqlTwo, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                Integer applicationId = rs.getInt(InstanceTable.APPLICATION_ID.getName());
                logger.debug("applicationId: {}", applicationId);
                Application application = new Application();
                application.setId(applicationId);
                application.setNumOfServer(rs.getInt("cnt"));
                applications.add(application);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return applications;
    }

    @Override
    public Instance getInstance(int instanceId) {
        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(GET_INSTANCE_SQL, InstanceTable.TABLE, InstanceTable.INSTANCE_ID.getName());
        Object[] params = new Object[] {instanceId};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                Instance instance = new Instance();
                instance.setId(rs.getString(InstanceTable.ID.getName()));
                instance.setApplicationId(rs.getInt(InstanceTable.APPLICATION_ID.getName()));
                instance.setAgentUUID(rs.getString(InstanceTable.AGENT_UUID.getName()));
                instance.setRegisterTime(rs.getLong(InstanceTable.REGISTER_TIME.getName()));
                instance.setHeartBeatTime(rs.getLong(InstanceTable.HEARTBEAT_TIME.getName()));
                instance.setOsInfo(rs.getString(InstanceTable.OS_INFO.getName()));
                return instance;
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<AppServerInfo> searchServer(String keyword, long startSecondTimeBucket, long endSecondTimeBucket) {
        logger.debug("get instances info, keyword: {}, start: {}, end: {}", keyword, startSecondTimeBucket, endSecondTimeBucket);
        String dynamicSql = "select * from {0} where {1} like ? and {2} >= ? and {3} <= ? and {4} = ?";
        String sqlOne = SqlBuilder.buildSql(dynamicSql, InstanceTable.TABLE, InstanceTable.OS_INFO.getName(), InstanceTable.REGISTER_TIME.getName(), InstanceTable.HEARTBEAT_TIME.getName(), InstanceTable.IS_ADDRESS.getName());
        String sqlTwo = SqlBuilder.buildSql(dynamicSql, InstanceTable.TABLE, InstanceTable.OS_INFO.getName(), InstanceTable.HEARTBEAT_TIME.getName(), InstanceTable.REGISTER_TIME.getName(), InstanceTable.IS_ADDRESS.getName());
        Object[] params = new Object[] {keyword, startSecondTimeBucket, endSecondTimeBucket, BooleanUtils.FALSE};
        
        List<AppServerInfo> result = new LinkedList<>();
        result.addAll(buildAppServerInfo(sqlOne, params));
        result.addAll(buildAppServerInfo(sqlTwo, params));
        return result;
    }

    @Override
    public List<AppServerInfo> getAllServer(int applicationId, long startSecondTimeBucket, long endSecondTimeBucket) {
        logger.debug("get instances info, applicationId: {}, startSecondTimeBucket: {}, endSecondTimeBucket: {}", applicationId, startSecondTimeBucket, endSecondTimeBucket);
        String dynamicSql = "select * from {0} where {1} = ? and {2} >= ? and {2} <= ? and {3} = ?";
        String sqlOne = SqlBuilder.buildSql(dynamicSql, InstanceTable.TABLE, InstanceTable.APPLICATION_ID.getName(), InstanceTable.REGISTER_TIME.getName(), InstanceTable.HEARTBEAT_TIME.getName(), InstanceTable.IS_ADDRESS.getName());
        String sqlTwo = SqlBuilder.buildSql(dynamicSql, InstanceTable.TABLE, InstanceTable.APPLICATION_ID.getName(), InstanceTable.HEARTBEAT_TIME.getName(), InstanceTable.REGISTER_TIME.getName(), InstanceTable.IS_ADDRESS.getName());
        Object[] params = new Object[] {applicationId, startSecondTimeBucket, endSecondTimeBucket, BooleanUtils.FALSE};
        
        List<AppServerInfo> result = new LinkedList<>();
        result.addAll(buildAppServerInfo(sqlOne, params));
        result.addAll(buildAppServerInfo(sqlTwo, params));
        return result;
    }

    private List<AppServerInfo> buildAppServerInfo(String sql, Object[] params) {
        ShardingjdbcClient client = getClient();

        List<AppServerInfo> appServerInfos = new LinkedList<>();
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                AppServerInfo appServerInfo = new AppServerInfo();
                appServerInfo.setId(rs.getInt(InstanceTable.INSTANCE_ID.getName()));
                appServerInfo.setOsInfo(rs.getString(InstanceTable.OS_INFO.getName()));
                appServerInfos.add(appServerInfo);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return appServerInfos;
    }

    @Override public long getEarliestRegisterTime(int applicationId) {
        ShardingjdbcClient client = getClient();
        
        String sql = SqlBuilder.buildSql(GET_REGISTER_TIME_SQL, InstanceTable.TABLE, InstanceTable.APPLICATION_ID.getName(), InstanceTable.REGISTER_TIME.getName());
        Object[] params = new Object[] {applicationId};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                return rs.getLong(InstanceTable.REGISTER_TIME.getName());
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    @Override public long getLatestHeartBeatTime(int applicationId) {
        ShardingjdbcClient client = getClient();
        
        String sql = SqlBuilder.buildSql(GET_HEARTBEAT_TIME_SQL, InstanceTable.TABLE, InstanceTable.APPLICATION_ID.getName(), InstanceTable.HEARTBEAT_TIME.getName());
        Object[] params = new Object[] {applicationId};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                return rs.getLong(InstanceTable.HEARTBEAT_TIME.getName());
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }
}
