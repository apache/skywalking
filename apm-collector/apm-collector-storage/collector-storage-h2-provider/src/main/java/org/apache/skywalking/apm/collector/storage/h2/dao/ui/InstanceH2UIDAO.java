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

package org.apache.skywalking.apm.collector.storage.h2.dao.ui;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.apache.skywalking.apm.collector.storage.ui.application.Application;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class InstanceH2UIDAO extends H2DAO implements IInstanceUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(InstanceH2UIDAO.class);

    public InstanceH2UIDAO(H2Client client) {
        super(client);
    }

    private static final String GET_LAST_HEARTBEAT_TIME_SQL = "select {0} from {1} where {2} > ? limit 1";
    private static final String GET_INST_LAST_HEARTBEAT_TIME_SQL = "select {0} from {1} where {2} > ? and {3} = ? limit 1";
    private static final String GET_INSTANCE_SQL = "select * from {0} where {1} = ?";
    private static final String GET_APPLICATIONS_SQL = "select {3}, count({0}) as cnt from {1} where {2} >= ? group by {3} limit 100";

    @Override
    public Long lastHeartBeatTime() {
        H2Client client = getClient();
        long fiveMinuteBefore = System.currentTimeMillis() - 5 * 60 * 1000;
        fiveMinuteBefore = TimeBucketUtils.INSTANCE.getSecondTimeBucket(fiveMinuteBefore);
        String sql = SqlBuilder.buildSql(GET_LAST_HEARTBEAT_TIME_SQL, InstanceTable.HEARTBEAT_TIME.getName(), InstanceTable.TABLE, InstanceTable.HEARTBEAT_TIME.getName());
        Object[] params = new Object[] {fiveMinuteBefore};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0L;
    }

    @Override
    public Long instanceLastHeartBeatTime(long applicationInstanceId) {
        H2Client client = getClient();
        long fiveMinuteBefore = System.currentTimeMillis() - 5 * 60 * 1000;
        fiveMinuteBefore = TimeBucketUtils.INSTANCE.getSecondTimeBucket(fiveMinuteBefore);
        String sql = SqlBuilder.buildSql(GET_INST_LAST_HEARTBEAT_TIME_SQL, InstanceTable.HEARTBEAT_TIME.getName(), InstanceTable.TABLE,
            InstanceTable.HEARTBEAT_TIME.getName(), InstanceTable.INSTANCE_ID.getName());
        Object[] params = new Object[] {fiveMinuteBefore, applicationInstanceId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0L;
    }

    @Override
    public List<Application> getApplications(long startSecondTimeBucket, long endSecondTimeBucket,
        int... applicationIds) {
        H2Client client = getClient();
        List<Application> applications = new LinkedList<>();
        String sql = SqlBuilder.buildSql(GET_APPLICATIONS_SQL, InstanceTable.INSTANCE_ID.getName(),
            InstanceTable.TABLE, InstanceTable.HEARTBEAT_TIME.getName(), InstanceTable.APPLICATION_ID.getName());
        Object[] params = new Object[] {startSecondTimeBucket};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                Integer applicationId = rs.getInt(InstanceTable.APPLICATION_ID.getName());
                logger.debug("applicationId: {}", applicationId);
                Application application = new Application();
                application.setId(applicationId);
                application.setNumOfServer(rs.getInt("cnt"));
                applications.add(application);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return applications;
    }

    @Override
    public Instance getInstance(int instanceId) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_INSTANCE_SQL, InstanceTable.TABLE, InstanceTable.INSTANCE_ID.getName());
        Object[] params = new Object[] {instanceId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
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
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<AppServerInfo> searchServer(String keyword, long startSecondTimeBucket, long endSecondTimeBucket) {
        logger.debug("get instances info, keyword: {}, start: {}, end: {}", keyword, startSecondTimeBucket, endSecondTimeBucket);
        String dynamicSql = "select * from {0} where {1} like ? and (({2} >= ? and {2} <= ?) or ({3} >= ? and {3} <= ?)) and {4} = ?";
        String sql = SqlBuilder.buildSql(dynamicSql, InstanceTable.TABLE, InstanceTable.OS_INFO.getName(), InstanceTable.REGISTER_TIME.getName(), InstanceTable.HEARTBEAT_TIME.getName(), InstanceTable.IS_ADDRESS.getName());
        Object[] params = new Object[] {keyword, startSecondTimeBucket, endSecondTimeBucket, startSecondTimeBucket, endSecondTimeBucket, BooleanUtils.FALSE};
        return buildAppServerInfo(sql, params);
    }

    @Override
    public List<AppServerInfo> getAllServer(int applicationId, long startSecondTimeBucket, long endSecondTimeBucket) {
        logger.debug("get instances info, applicationId: {}, startSecondTimeBucket: {}, endSecondTimeBucket: {}", applicationId, startSecondTimeBucket, endSecondTimeBucket);
        String dynamicSql = "select * from {0} where {1} = ? and (({2} >= ? and {2} <= ?) or ({3} >= ? and {3} <= ?)) and {4} = ?";
        String sql = SqlBuilder.buildSql(dynamicSql, InstanceTable.TABLE, InstanceTable.APPLICATION_ID.getName(), InstanceTable.REGISTER_TIME.getName(), InstanceTable.HEARTBEAT_TIME.getName(), InstanceTable.IS_ADDRESS.getName());
        Object[] params = new Object[] {applicationId, startSecondTimeBucket, endSecondTimeBucket, startSecondTimeBucket, endSecondTimeBucket, BooleanUtils.FALSE};
        return buildAppServerInfo(sql, params);
    }

    private List<AppServerInfo> buildAppServerInfo(String sql, Object[] params) {
        H2Client client = getClient();

        List<AppServerInfo> appServerInfos = new LinkedList<>();
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                AppServerInfo appServerInfo = new AppServerInfo();
                appServerInfo.setId(rs.getInt(InstanceTable.INSTANCE_ID.getName()));
                appServerInfo.setOsInfo(rs.getString(InstanceTable.OS_INFO.getName()));
                appServerInfos.add(appServerInfo);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return appServerInfos;
    }

    //TODO
    @Override public long getEarliestRegisterTime(int applicationId) {
        return 0;
    }

    //TODO
    @Override public long getLatestHeartBeatTime(int applicationId) {
        return 0;
    }
}
