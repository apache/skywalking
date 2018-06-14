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
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class ApplicationReferenceMetricShardingjdbcUIDAO extends ShardingjdbcDAO implements IApplicationReferenceMetricUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationReferenceMetricShardingjdbcUIDAO.class);
    private static final String APPLICATION_REFERENCE_SQL_ONE = "select {7}, {8}, sum({0}) as {0}, sum({1}) as {1}, sum({2}) as {2}, " +
        "sum({3}) as {3} from {4} where {5} >= ? and {5} <= ? and {6} = ? and {7} in (?) group by {7}, {8} limit 100";
    private static final String APPLICATION_REFERENCE_SQL_TWO = "select {7}, {8}, sum({0}) as {0}, sum({1}) as {1}, sum({2}) as {2}, " +
            "sum({3}) as {3} from {4} where {5} >= ? and {5} <= ? and {6} = ? and {8} in (?) group by {7}, {8} limit 100";

    public ApplicationReferenceMetricShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public List<ApplicationReferenceMetric> getReferences(Step step,
        long startTimeBucket, long endTimeBucket, MetricSource metricSource, Integer... applicationIds) {
        ShardingjdbcClient client = getClient();
        
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationReferenceMetricTable.TABLE);
        
        List<ApplicationReferenceMetric> referenceMetrics = new LinkedList<>();
        String sqlOne = SqlBuilder.buildSql(APPLICATION_REFERENCE_SQL_ONE, ApplicationReferenceMetricTable.TRANSACTION_CALLS.getName(), 
                ApplicationReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName(), ApplicationReferenceMetricTable.TRANSACTION_DURATION_SUM.getName(), 
                ApplicationReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName(), tableName, ApplicationReferenceMetricTable.TIME_BUCKET.getName(), 
                ApplicationReferenceMetricTable.SOURCE_VALUE.getName(), ApplicationReferenceMetricTable.FRONT_APPLICATION_ID.getName(), 
                ApplicationReferenceMetricTable.BEHIND_APPLICATION_ID.getName());
        String sqlTwo = SqlBuilder.buildSql(APPLICATION_REFERENCE_SQL_TWO, ApplicationReferenceMetricTable.TRANSACTION_CALLS.getName(), 
                ApplicationReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName(), ApplicationReferenceMetricTable.TRANSACTION_DURATION_SUM.getName(), 
                ApplicationReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName(), tableName, ApplicationReferenceMetricTable.TIME_BUCKET.getName(), 
                ApplicationReferenceMetricTable.SOURCE_VALUE.getName(), ApplicationReferenceMetricTable.FRONT_APPLICATION_ID.getName(), 
                ApplicationReferenceMetricTable.BEHIND_APPLICATION_ID.getName());
        
        String applicationIdsParam = Arrays.toString(applicationIds).replace("[", "").replace("]", "");
        Object[] params = new Object[] {startTimeBucket, endTimeBucket, metricSource.getValue(), applicationIdsParam};
        
        try (
                ResultSet rs = client.executeQuery(sqlOne, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                int sourceApplicationId = rs.getInt(ApplicationReferenceMetricTable.FRONT_APPLICATION_ID.getName());
                int targetApplicationId = rs.getInt(ApplicationReferenceMetricTable.BEHIND_APPLICATION_ID.getName());
                long calls = rs.getLong(ApplicationReferenceMetricTable.TRANSACTION_CALLS.getName());
                long errorCalls = rs.getLong(ApplicationReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName());
                long durations = rs.getLong(ApplicationReferenceMetricTable.TRANSACTION_DURATION_SUM.getName());
                long errorDurations = rs.getLong(ApplicationReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName());

                ApplicationReferenceMetric referenceMetric = new ApplicationReferenceMetric();
                referenceMetric.setSource(sourceApplicationId);
                referenceMetric.setTarget(targetApplicationId);
                referenceMetric.setCalls(calls);
                referenceMetric.setErrorCalls(errorCalls);
                referenceMetric.setDurations(durations);
                referenceMetric.setErrorDurations(errorDurations);
                referenceMetrics.add(referenceMetric);
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
                int sourceApplicationId = rs.getInt(ApplicationReferenceMetricTable.FRONT_APPLICATION_ID.getName());
                int targetApplicationId = rs.getInt(ApplicationReferenceMetricTable.BEHIND_APPLICATION_ID.getName());
                long calls = rs.getLong(ApplicationReferenceMetricTable.TRANSACTION_CALLS.getName());
                long errorCalls = rs.getLong(ApplicationReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName());
                long durations = rs.getLong(ApplicationReferenceMetricTable.TRANSACTION_DURATION_SUM.getName());
                long errorDurations = rs.getLong(ApplicationReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName());

                ApplicationReferenceMetric referenceMetric = new ApplicationReferenceMetric();
                referenceMetric.setSource(sourceApplicationId);
                referenceMetric.setTarget(targetApplicationId);
                referenceMetric.setCalls(calls);
                referenceMetric.setErrorCalls(errorCalls);
                referenceMetric.setDurations(durations);
                referenceMetric.setErrorDurations(errorDurations);
                referenceMetrics.add(referenceMetric);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        
        return referenceMetrics.size() > 100 ? referenceMetrics.subList(0, 100) : referenceMetrics;
    }
}
