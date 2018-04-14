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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Node;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceMetric;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        String sql = SqlBuilder.buildSql(dynamicSql, tableName, ServiceMetricTable.ID.getName());

        List<Integer> trends = new LinkedList<>();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + serviceId + Const.ID_SPLIT + MetricSource.Callee.getValue();

            try (ResultSet rs = client.executeQuery(sql, new String[] {id})) {
                if (rs.next()) {
                    long calls = rs.getLong(ServiceMetricTable.TRANSACTION_CALLS.getName());
                    long durationSum = rs.getLong(ServiceMetricTable.TRANSACTION_DURATION_SUM.getName());
                    trends.add((int) (durationSum / calls));
                } else {
                    trends.add(0);
                }
            } catch (SQLException | H2ClientException e) {
                logger.error(e.getMessage(), e);
            }
        });

        return trends;
    }

    @Override public List<Integer> getServiceTPSTrend(int serviceId, Step step, List<DurationPoint> durationPoints) {
        return null;
    }

    @Override public List<Integer> getServiceSLATrend(int serviceId, Step step, List<DurationPoint> durationPoints) {
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceMetricTable.TABLE);

        H2Client client = getClient();
        String dynamicSql = "select * from {0} where {1} = ?";
        String sql = SqlBuilder.buildSql(dynamicSql, tableName, ServiceMetricTable.ID);

        List<Integer> trends = new LinkedList<>();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + serviceId + Const.ID_SPLIT + MetricSource.Callee.getValue();

            try (ResultSet rs = client.executeQuery(sql, new String[] {id})) {
                if (rs.next()) {
                    long calls = rs.getLong(ServiceMetricTable.TRANSACTION_CALLS.getName());
                    long errorCalls = rs.getLong(ServiceMetricTable.TRANSACTION_ERROR_CALLS.getName());
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

    @Override public List<Node> getServicesMetric(Step step, long startTime, long endTime, MetricSource metricSource,
        Collection<Integer> serviceIds) {
        return null;
    }

    @Override public List<ServiceMetric> getSlowService(int applicationId, Step step, long startTimeBucket, long endTimeBucket, Integer topN,
        MetricSource metricSource) {
        return null;
    }
}
