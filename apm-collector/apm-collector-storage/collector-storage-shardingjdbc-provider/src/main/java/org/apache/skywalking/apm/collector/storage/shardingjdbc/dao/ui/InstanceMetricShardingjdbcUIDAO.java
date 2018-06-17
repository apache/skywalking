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
import java.util.LinkedList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class InstanceMetricShardingjdbcUIDAO extends ShardingjdbcDAO implements IInstanceMetricUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(InstanceMetricShardingjdbcUIDAO.class);
    private static final String GET_THROUGHPUT_METRIC_SQL = "select {1}, sum({2}) as {2} from {3} where {4} >= ? and {4} <= ? and {5} = ? and {1} = ? group by {0} order by {2} desc limit ?";
    private static final String GET_TPS_METRIC_SQL = "select * from {0} where {1} = ?";

    public InstanceMetricShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public List<AppServerInfo> getServerThroughput(int applicationId, Step step, long startTimeBucket, long endTimeBucket,
        int minutesBetween, int topN, MetricSource metricSource) {
        ShardingjdbcClient client = getClient();
        
        String tableName = TimePyramidTableNameBuilder.build(step, InstanceMetricTable.TABLE);
        
        List<AppServerInfo> appServerInfos = new LinkedList<>();
        String sql = SqlBuilder.buildSql(GET_THROUGHPUT_METRIC_SQL, InstanceMetricTable.INSTANCE_ID.getName(), 
                InstanceMetricTable.APPLICATION_ID.getName(), InstanceMetricTable.TRANSACTION_CALLS.getName(), 
                tableName, InstanceMetricTable.TIME_BUCKET.getName(), InstanceMetricTable.SOURCE_VALUE.getName());
        
        Object[] params = new Object[] {startTimeBucket, endTimeBucket, metricSource.getValue(), applicationId, topN};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                int instanceId = rs.getInt(InstanceMetricTable.INSTANCE_ID.getName());
                long calls = rs.getLong(InstanceMetricTable.TRANSACTION_CALLS.getName());
                int callsPerMinute = (int)(minutesBetween == 0 ? 0 : calls / minutesBetween);

                AppServerInfo appServerInfo = new AppServerInfo();
                appServerInfo.setId(instanceId);
                appServerInfo.setCpm(callsPerMinute);
                appServerInfos.add(appServerInfo);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        
        return appServerInfos;
    }

    @Override public List<Integer> getServerThroughputTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        ShardingjdbcClient client = getClient();
        String tableName = TimePyramidTableNameBuilder.build(step, InstanceMetricTable.TABLE);

        String sql = SqlBuilder.buildSql(GET_TPS_METRIC_SQL, tableName, InstanceMetricTable.ID.getName());

        List<Integer> throughputTrend = new LinkedList<>();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + MetricSource.Callee.getValue();
            try (
                    ResultSet rs = client.executeQuery(sql, new Object[] {id});
                    Statement statement = rs.getStatement();
                    Connection conn = statement.getConnection();
                ) {
                if (rs.next()) {
                    long callTimes = rs.getLong(InstanceMetricTable.TRANSACTION_CALLS.getName());
                    throughputTrend.add((int)(callTimes / durationPoint.getSecondsBetween()));
                } else {
                    throughputTrend.add(0);
                }
            } catch (SQLException | ShardingjdbcClientException e) {
                logger.error(e.getMessage(), e);
            }
        });

        return throughputTrend;
    }

    @Override public List<Integer> getResponseTimeTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        ShardingjdbcClient client = getClient();

        String tableName = TimePyramidTableNameBuilder.build(step, InstanceMetricTable.TABLE);
        String sql = SqlBuilder.buildSql(GET_TPS_METRIC_SQL, tableName, InstanceMetricTable.ID.getName());

        List<Integer> responseTimeTrends = new LinkedList<>();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + MetricSource.Callee.getValue();
            try (
                    ResultSet rs = client.executeQuery(sql, new Object[] {id});
                    Statement statement = rs.getStatement();
                    Connection conn = statement.getConnection();
                ) {
                if (rs.next()) {
                    long callTimes = rs.getLong(InstanceMetricTable.TRANSACTION_CALLS.getName());
                    long durationSum = rs.getLong(InstanceMetricTable.TRANSACTION_DURATION_SUM.getName());
                    responseTimeTrends.add((int) (durationSum / callTimes));
                } else {
                    responseTimeTrends.add(0);
                }
            } catch (SQLException | ShardingjdbcClientException e) {
                logger.error(e.getMessage(), e);
            }
        });
        return responseTimeTrends;
    }
}
