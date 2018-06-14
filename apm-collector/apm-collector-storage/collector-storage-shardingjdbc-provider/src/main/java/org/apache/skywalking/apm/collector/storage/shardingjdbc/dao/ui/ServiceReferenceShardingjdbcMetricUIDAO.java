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
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class ServiceReferenceShardingjdbcMetricUIDAO extends ShardingjdbcDAO implements IServiceReferenceMetricUIDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceReferenceShardingjdbcMetricUIDAO.class);
    private static final String SERVICE_REFERENCE_FRONT_SQL = "select {0}, sum({1}) as {1}, sum({2}) as {2}, sum({3}) as {3}, sum({4}) as {4} from {5} where {6} >= ? and {6} <= ? and {7} = ? and {8} = ? group by {0} limit 100";
    private static final String SERVICE_REFERENCE_BEHIND_SQL = "select {0}, sum({1}) as {1}, sum({2}) as {2}, sum({3}) as {3}, sum({4}) as {4} from {5} where {6} >= ? and {6} <= ? and {7} = ? and {8} = ? group by {8} limit 100";

    public ServiceReferenceShardingjdbcMetricUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public List<ServiceReferenceMetric> getFrontServices(Step step, long startTimeBucket, long endTimeBucket,
        MetricSource metricSource,
        int behindServiceId) {
        ShardingjdbcClient client = getClient();
        
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceReferenceMetricTable.TABLE);
        
        List<ServiceReferenceMetric> referenceMetrics = new LinkedList<>();
        String sql = SqlBuilder.buildSql(SERVICE_REFERENCE_FRONT_SQL, ServiceReferenceMetricTable.FRONT_SERVICE_ID.getName(), 
                ServiceReferenceMetricTable.TRANSACTION_CALLS.getName(), ServiceReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName(), 
                ServiceReferenceMetricTable.TRANSACTION_DURATION_SUM.getName(), ServiceReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName(), 
                tableName, ServiceReferenceMetricTable.TIME_BUCKET.getName(), ServiceReferenceMetricTable.SOURCE_VALUE.getName(), 
                ServiceReferenceMetricTable.BEHIND_SERVICE_ID.getName());
        
        Object[] params = new Object[] {startTimeBucket, endTimeBucket, metricSource.getValue(), behindServiceId};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                int frontServiceId = rs.getInt(ServiceReferenceMetricTable.FRONT_SERVICE_ID.getName());
                long callsSum = rs.getLong(ServiceReferenceMetricTable.TRANSACTION_CALLS.getName());
                long errorCallsSum = rs.getLong(ServiceReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName());
                long durationSum = rs.getLong(ServiceReferenceMetricTable.TRANSACTION_DURATION_SUM.getName());
                long errorDurationSum = rs.getLong(ServiceReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName());

                ServiceReferenceMetric referenceMetric = new ServiceReferenceMetric();
                referenceMetric.setSource(frontServiceId);
                referenceMetric.setTarget(behindServiceId);
                referenceMetric.setCalls(callsSum);
                referenceMetric.setErrorCalls(errorCallsSum);
                referenceMetric.setDurations(durationSum);
                referenceMetric.setErrorDurations(errorDurationSum);
                referenceMetrics.add(referenceMetric);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        
        return referenceMetrics;
    }

    @Override public List<ServiceReferenceMetric> getBehindServices(Step step, long startTimeBucket, long endTimeBucket,
        MetricSource metricSource,
        int frontServiceId) {
        ShardingjdbcClient client = getClient();
        
        String tableName = TimePyramidTableNameBuilder.build(step, ServiceReferenceMetricTable.TABLE);
        
        List<ServiceReferenceMetric> referenceMetrics = new LinkedList<>();
        String sql = SqlBuilder.buildSql(SERVICE_REFERENCE_BEHIND_SQL, ServiceReferenceMetricTable.BEHIND_SERVICE_ID.getName(), 
                ServiceReferenceMetricTable.TRANSACTION_CALLS.getName(), ServiceReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName(), 
                ServiceReferenceMetricTable.TRANSACTION_DURATION_SUM.getName(), ServiceReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName(), 
                tableName, ServiceReferenceMetricTable.TIME_BUCKET.getName(), ServiceReferenceMetricTable.SOURCE_VALUE.getName(), 
                ServiceReferenceMetricTable.FRONT_SERVICE_ID.getName());
        
        Object[] params = new Object[] {startTimeBucket, endTimeBucket, metricSource.getValue(), frontServiceId};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                int behindServiceId = rs.getInt(ServiceReferenceMetricTable.BEHIND_SERVICE_ID.getName());
                long callsSum = rs.getLong(ServiceReferenceMetricTable.TRANSACTION_CALLS.getName());
                long errorCallsSum = rs.getLong(ServiceReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName());
                long durationSum = rs.getLong(ServiceReferenceMetricTable.TRANSACTION_DURATION_SUM.getName());
                long errorDurationSum = rs.getLong(ServiceReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName());

                ServiceReferenceMetric referenceMetric = new ServiceReferenceMetric();
                referenceMetric.setSource(frontServiceId);
                referenceMetric.setTarget(behindServiceId);
                referenceMetric.setCalls(callsSum);
                referenceMetric.setErrorCalls(errorCallsSum);
                referenceMetric.setDurations(durationSum);
                referenceMetric.setErrorDurations(errorDurationSum);
                referenceMetrics.add(referenceMetric);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        
        return referenceMetrics;
    }
}
