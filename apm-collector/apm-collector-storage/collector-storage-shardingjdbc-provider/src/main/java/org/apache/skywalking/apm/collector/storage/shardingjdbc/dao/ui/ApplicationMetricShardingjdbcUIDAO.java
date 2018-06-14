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

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.overview.ApplicationThroughput;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

/**
 * @author linjiaqi
 */
public class ApplicationMetricShardingjdbcUIDAO extends ShardingjdbcDAO implements IApplicationMetricUIDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(ApplicationMetricShardingjdbcUIDAO.class);
    private static final String APPLICATION_THROUGHPUT_TOPN_SQL = "select {0}, sum({1}) as {1} from {2} where {3} >= ? and {3} <= ? and {4} = ? group by {0} order by {1} desc limit ?";
    private static final String APPLICATION_GET_SQL = "select {0}, sum({1}) as {1}, sum({2}) as {2}, sum({3}) as {3}, sum({4}) as {4}, sum({5}) as {5}, sum({6}) as {6}, sum({7}) as {7} from {8} " +
            "where {9} >= ? and {9} <= ? and {10} = ? group by {0} limit 100";
    
    public ApplicationMetricShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override
    public List<ApplicationThroughput> getTopNApplicationThroughput(Step step, long startTimeBucket, long endTimeBucket,
                                                                    int minutesBetween, int topN, MetricSource metricSource) {
        ShardingjdbcClient client = getClient();
        
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationMetricTable.TABLE);
        
        List<ApplicationThroughput> applicationThroughputList = new LinkedList<>();
        String sql = SqlBuilder.buildSql(APPLICATION_THROUGHPUT_TOPN_SQL, ApplicationMetricTable.APPLICATION_ID.getName(),
                ApplicationMetricTable.TRANSACTION_CALLS.getName(), tableName, ApplicationMetricTable.TIME_BUCKET.getName(),
                ApplicationMetricTable.SOURCE_VALUE.getName());
        
        Object[] params = new Object[]{startTimeBucket, endTimeBucket, metricSource.getValue(), topN};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
        ) {
            while (rs.next()) {
                int applicationId = rs.getInt(ApplicationMetricTable.APPLICATION_ID.getName());
                long calls = rs.getLong(ApplicationMetricTable.TRANSACTION_CALLS.getName());
                int callsPerMinute = (int) (minutesBetween == 0 ? 0 : calls / minutesBetween);
                
                ApplicationThroughput applicationThroughput = new ApplicationThroughput();
                applicationThroughput.setApplicationId(applicationId);
                applicationThroughput.setCpm(callsPerMinute);
                applicationThroughputList.add(applicationThroughput);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        
        return applicationThroughputList;
    }
    
    @Override
    public List<ApplicationMetric> getApplications(Step step, long startTimeBucket,
                                                   long endTimeBucket, MetricSource metricSource) {
        ShardingjdbcClient client = getClient();
        
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationMetricTable.TABLE);
        
        List<ApplicationMetric> applicationMetrics = new LinkedList<>();
        String sql = SqlBuilder.buildSql(APPLICATION_GET_SQL, ApplicationMetricTable.APPLICATION_ID.getName(),
                ApplicationMetricTable.TRANSACTION_CALLS.getName(), ApplicationMetricTable.TRANSACTION_ERROR_CALLS.getName(),
                ApplicationMetricTable.TRANSACTION_DURATION_SUM.getName(), ApplicationMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName(),
                ApplicationMetricTable.SATISFIED_COUNT.getName(), ApplicationMetricTable.TOLERATING_COUNT.getName(),
                ApplicationMetricTable.FRUSTRATED_COUNT.getName(), tableName, ApplicationMetricTable.TIME_BUCKET.getName(),
                ApplicationMetricTable.SOURCE_VALUE.getName());
        
        Object[] params = new Object[]{startTimeBucket, endTimeBucket, metricSource.getValue()};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
        ) {
            while (rs.next()) {
                int applicationId = rs.getInt(ApplicationMetricTable.APPLICATION_ID.getName());
                long calls = rs.getLong(ApplicationMetricTable.TRANSACTION_CALLS.getName());
                long errorCalls = rs.getLong(ApplicationMetricTable.TRANSACTION_ERROR_CALLS.getName());
                long durations = rs.getLong(ApplicationMetricTable.TRANSACTION_DURATION_SUM.getName());
                long errorDurations = rs.getLong(ApplicationMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName());
                long satisfiedCount = rs.getLong(ApplicationMetricTable.SATISFIED_COUNT.getName());
                long toleratingCount = rs.getLong(ApplicationMetricTable.TOLERATING_COUNT.getName());
                long frustratedCount = rs.getLong(ApplicationMetricTable.FRUSTRATED_COUNT.getName());
                
                ApplicationMetric applicationMetric = new ApplicationMetric();
                applicationMetric.setId(applicationId);
                applicationMetric.setCalls(calls);
                applicationMetric.setErrorCalls(errorCalls);
                applicationMetric.setDurations(durations);
                applicationMetric.setErrorDurations(errorDurations);
                applicationMetric.setSatisfiedCount(satisfiedCount);
                applicationMetric.setToleratingCount(toleratingCount);
                applicationMetric.setFrustratedCount(frustratedCount);
                applicationMetrics.add(applicationMetric);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        
        return applicationMetrics;
    }
}
