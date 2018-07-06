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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Node;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceMetric;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceNode;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class ServiceMetricShardingjdbcUIDAO extends ShardingjdbcDAO implements IServiceMetricUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(ServiceMetricShardingjdbcUIDAO.class);
    private static final String SERVICE_METRIC_GET_SQL = "select {0}, sum({1}) as {1}, sum({2}) as {2} from {3} where {4} >= ? and {4} <= ? and {5} = ? and {0} in (?) group by {0} limit 100";
    private static final String SLOW_SERVICE_GET_SQL = "select {0}, {1}, {2} from {3} where {4} >= ? and {4} <= ? and {5} = ? and {6} = ? order by {2} desc limit ?";

    public ServiceMetricShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override
    public List<Integer> getServiceResponseTimeTrend(int serviceId, Step step, List<DurationPoint> durationPoints) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);

        ShardingjdbcClient client = getClient();
        String dynamicSql = "select * from {0} where {1} = ?";
        String sql = SqlBuilder.buildSql(dynamicSql, tableName, ServiceMetricTable.ID.getName());

        List<Integer> trends = new LinkedList<>();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + serviceId + Const.ID_SPLIT + MetricSource.Callee.getValue();

            try (
                    ResultSet rs = client.executeQuery(sql, new String[] {id});
                    Statement statement = rs.getStatement();
                    Connection conn = statement.getConnection();
                ) {
                if (rs.next()) {
                    long calls = rs.getLong(ServiceMetricTable.TRANSACTION_CALLS.getName());
                    long durationSum = rs.getLong(ServiceMetricTable.TRANSACTION_DURATION_SUM.getName());
                    trends.add((int) (durationSum / calls));
                } else {
                    trends.add(0);
                }
            } catch (SQLException | ShardingjdbcClientException e) {
                logger.error(e.getMessage(), e);
            }
        });

        return trends;
    }
    
    @Override public List<Integer> getServiceThroughputTrend(int serviceId, Step step, List<DurationPoint> durationPoints) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);

        ShardingjdbcClient client = getClient();
        String dynamicSql = "select * from {0} where {1} = ?";
        String sql = SqlBuilder.buildSql(dynamicSql, tableName, ServiceMetricTable.ID.getName());

        List<Integer> trends = new LinkedList<>();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + serviceId + Const.ID_SPLIT + MetricSource.Callee.getValue();

            try (
                    ResultSet rs = client.executeQuery(sql, new String[] {id});
                    Statement statement = rs.getStatement();
                    Connection conn = statement.getConnection();
                ) {
                if (rs.next()) {
                    long calls = rs.getLong(ServiceMetricTable.TRANSACTION_CALLS.getName());
                    long secondsBetween = durationPoint.getSecondsBetween();
                    trends.add(secondsBetween == 0 ? 0 : (int) (calls / secondsBetween));
                } else {
                    trends.add(0);
                }
            } catch (SQLException | ShardingjdbcClientException e) {
                logger.error(e.getMessage(), e);
            }
        });

        return trends;
    }

    @Override public List<Integer> getServiceSLATrend(int serviceId, Step step, List<DurationPoint> durationPoints) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);

        ShardingjdbcClient client = getClient();
        String dynamicSql = "select * from {0} where {1} = ?";
        String sql = SqlBuilder.buildSql(dynamicSql, tableName, ServiceMetricTable.ID);

        List<Integer> trends = new LinkedList<>();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + serviceId + Const.ID_SPLIT + MetricSource.Callee.getValue();

            try (
                    ResultSet rs = client.executeQuery(sql, new String[] {id});
                    Statement statement = rs.getStatement();
                    Connection conn = statement.getConnection();
                ) {
                if (rs.next()) {
                    long calls = rs.getLong(ServiceMetricTable.TRANSACTION_CALLS.getName());
                    long errorCalls = rs.getLong(ServiceMetricTable.TRANSACTION_ERROR_CALLS.getName());
                    trends.add((int)(((calls - errorCalls) / calls)) * 10000);
                } else {
                    trends.add(10000);
                }
            } catch (SQLException | ShardingjdbcClientException e) {
                logger.error(e.getMessage(), e);
            }
        });

        return trends;
    }

    @Override public List<Node> getServicesMetric(Step step, long startTimeBucket, long endTimeBucket, MetricSource metricSource,
        Collection<Integer> serviceIds) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);

        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(SERVICE_METRIC_GET_SQL, ServiceMetricTable.SERVICE_ID.getName(), ServiceMetricTable.TRANSACTION_CALLS.getName(), 
                ServiceMetricTable.TRANSACTION_ERROR_CALLS.getName(), tableName, ServiceMetricTable.TIME_BUCKET.getName(), 
                ServiceMetricTable.SOURCE_VALUE.getName());

        List<Node> nodes = new LinkedList<>();
        String serviceIdsParam = serviceIds.toString().replace("[", "").replace("]", "");
        Object[] params = new Object[] {startTimeBucket, endTimeBucket, metricSource.getValue(), serviceIdsParam};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                int serviceId = rs.getInt(ServiceMetricTable.SERVICE_ID.getName());
                long callsSum = rs.getLong(ServiceMetricTable.TRANSACTION_CALLS.getName());
                long errorCallsSum = rs.getLong(ServiceMetricTable.TRANSACTION_ERROR_CALLS.getName());
                
                ServiceNode serviceNode = new ServiceNode();
                serviceNode.setId(serviceId);
                serviceNode.setCalls(callsSum);
                serviceNode.setSla((int)((callsSum - errorCallsSum) / callsSum * 10000));
                nodes.add(serviceNode);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }

        return nodes;
    }

    @Override public List<ServiceMetric> getSlowService(int applicationId, Step step, long startTimeBucket, long endTimeBucket, Integer topN,
        MetricSource metricSource) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);

        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(SLOW_SERVICE_GET_SQL, ServiceMetricTable.SERVICE_ID.getName(), ServiceMetricTable.TRANSACTION_CALLS.getName(), 
                ServiceMetricTable.TRANSACTION_AVERAGE_DURATION.getName(), tableName, ServiceMetricTable.TIME_BUCKET.getName(), 
                ServiceMetricTable.SOURCE_VALUE.getName(), ServiceMetricTable.APPLICATION_ID.getName());

        Set<Integer> serviceIds = new HashSet<>();
        List<ServiceMetric> serviceMetrics = new LinkedList<>();
        Object[] params = new Object[] {startTimeBucket, endTimeBucket, metricSource.getValue(), applicationId, topN * 60};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                int serviceId = rs.getInt(ServiceMetricTable.SERVICE_ID.getName());
                if (!serviceIds.contains(serviceId)) {
                    ServiceMetric serviceMetric = new ServiceMetric();
                    serviceMetric.getService().setId(serviceId);
                    serviceMetric.getService().setApplicationId(applicationId);
                    serviceMetric.setCalls(rs.getLong(ServiceMetricTable.TRANSACTION_CALLS.getName()));
                    serviceMetric.setAvgResponseTime(rs.getInt(ServiceMetricTable.TRANSACTION_AVERAGE_DURATION.getName()));
                    serviceMetrics.add(serviceMetric);
                    
                    serviceIds.add(serviceId);
                }
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }

        return serviceMetrics;
    }
}
