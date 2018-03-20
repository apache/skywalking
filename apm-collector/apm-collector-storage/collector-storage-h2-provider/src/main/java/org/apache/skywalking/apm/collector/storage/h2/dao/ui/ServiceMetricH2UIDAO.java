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

import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Node;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceMetric;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceNode;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author peng-yongsheng
 */
public class ServiceMetricH2UIDAO extends H2DAO implements IServiceMetricUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceMetricH2UIDAO.class);

    public ServiceMetricH2UIDAO(H2Client client) {
        super(client);
    }

    @Override
    public List<Integer> getServiceResponseTimeTrend(int serviceId, Step step, List<DurationPoint> durationPoints) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);

        H2Client client = getClient();
        String dynamicSql = "select * from {0} where {1} = ?";
        String sql = SqlBuilder.buildSql(dynamicSql, tableName, ServiceMetricTable.COLUMN_ID);

        List<Integer> trends = new LinkedList<>();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + serviceId + Const.ID_SPLIT + MetricSource.Callee.getValue();

            try (ResultSet rs = client.executeQuery(sql, new String[] {id})) {
                if (rs.next()) {
                    long calls = rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_CALLS);
                    long errorCalls = rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS);
                    long durationSum = rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);
                    long errorDurationSum = rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM);
                    trends.add((int)((durationSum - errorDurationSum) / (calls - errorCalls)));
                } else {
                    trends.add(0);
                }
            } catch (SQLException | H2ClientException e) {
                logger.error(e.getMessage(), e);
            }
        });

        return trends;
    }
    /**
     * @author wen-gang.ji
     */
    @Override public List<Integer> getServiceTPSTrend(int serviceId, Step step, List<DurationPoint> durationPoints) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);
        H2Client client = getClient();
        String dynamicSql = "select * from {0} where {1} = ?";
        String sql = SqlBuilder.buildSql(dynamicSql, tableName, ServiceMetricTable.COLUMN_ID);

        List<Integer> trends = new LinkedList<>();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + serviceId + Const.ID_SPLIT + MetricSource.Callee.getValue();
            try (ResultSet rs = client.executeQuery(sql, new String[] {id})) {
                int index = 0;
                if (rs.next()) {
                    long calls = rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_CALLS);
                    long secondBetween = durationPoints.get(index).getSecondsBetween();
                    trends.add((int)(calls / secondBetween));
                } else {
                    trends.add(0);
                }
                index++;
            } catch (SQLException | H2ClientException e) {
                logger.error(e.getMessage(), e);
            }
        });
        return trends;
    }

    @Override public List<Integer> getServiceSLATrend(int serviceId, Step step, List<DurationPoint> durationPoints) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);

        H2Client client = getClient();
        String dynamicSql = "select * from {0} where {1} = ?";
        String sql = SqlBuilder.buildSql(dynamicSql, tableName, ServiceMetricTable.COLUMN_ID);

        List<Integer> trends = new LinkedList<>();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + serviceId + Const.ID_SPLIT + MetricSource.Callee.getValue();

            try (ResultSet rs = client.executeQuery(sql, new String[] {id})) {
                if (rs.next()) {
                    long calls = rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_CALLS);
                    long errorCalls = rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS);
                    trends.add((int)(((calls - errorCalls) / calls)) * 10000);
                } else {
                    trends.add(10000);
                }
            } catch (SQLException | H2ClientException e) {
                logger.error(e.getMessage(), e);
            }
        });

        return trends;
    }
    /**
     * @author wen-gang.ji
     */
    @Override public List<Node> getServicesMetric(Step step, long startTime, long endTime, MetricSource metricSource,
        Collection<Integer> serviceIds) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);
        H2Client client = getClient();
        String dynamicSql = "select {2},sum({4}) as {4}, sum({5}) as {5}, from {0} where {1} >= ? and {1} <= ? and {2} in ? and {3} = ? group by {2} limit 100";
        String sql = SqlBuilder.buildSql(dynamicSql, tableName, ServiceMetricTable.COLUMN_TIME_BUCKET,ServiceMetricTable.COLUMN_SERVICE_ID,ServiceMetricTable.COLUMN_SOURCE_VALUE,
                ServiceMetricTable.COLUMN_TRANSACTION_CALLS,ServiceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS);

        Object[] params = new Object[] {startTime, endTime, serviceIds, metricSource.getValue()};
        List<Node> nodes = new LinkedList<>();
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                int serviceId =rs.getInt(ServiceMetricTable.COLUMN_SERVICE_ID);
                Long callsSum = rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_CALLS);
                Long errorCallsSum = rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS);

                ServiceNode serviceNode = new ServiceNode();
                serviceNode.setId(serviceId);
                serviceNode.setCalls(callsSum);
                serviceNode.setSla((int)(((callsSum - errorCallsSum) / callsSum) * 10000));
                nodes.add(serviceNode);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }

        return nodes;

    }

    /**
     * @author wen-gang.ji
     */
    @Override public List<ServiceMetric> getSlowService(int applicationId, Step step, long startTimeBucket, long endTimeBucket, Integer topN,
        MetricSource metricSource) {
        topN = topN * 60;
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);
        H2Client client = getClient();
        String dynamicSql = "select {0},{1},{2} from {3} where {4} >= ? and {4} <= ? and {5} = ?  and {6} = ? order by {7} desc limit ?";
        String sql = SqlBuilder.buildSql(dynamicSql, ServiceMetricTable.COLUMN_SERVICE_ID,ServiceMetricTable.COLUMN_TRANSACTION_CALLS,ServiceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION
        ,tableName,ServiceMetricTable.COLUMN_TIME_BUCKET,ServiceMetricTable.COLUMN_APPLICATION_ID,ServiceMetricTable.COLUMN_SOURCE_VALUE,ServiceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION);
        Object[] params = new Object[] {startTimeBucket,endTimeBucket,applicationId,metricSource.getValue(),topN};
        Set<Integer> serviceIds = new HashSet<>();
        List<ServiceMetric> serviceMetrics = new LinkedList<>();
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                int serviceId = rs.getInt(ServiceNameTable.COLUMN_SERVICE_ID);
                if (!serviceIds.contains(serviceId)){
                    ServiceMetric serviceMetric = new ServiceMetric();
                    serviceMetric.setId(serviceId);
                    serviceMetric.setCalls(rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_CALLS));
                    serviceMetric.setAvgResponseTime(rs.getInt(ServiceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION));
                    serviceMetrics.add(serviceMetric);

                    serviceIds.add(serviceId);
                }
                if (topN == serviceIds.size()) {
                    break;
                }
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return serviceMetrics;

    }
}
