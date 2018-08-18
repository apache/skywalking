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
import java.util.List;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IMemoryMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class MemoryMetricShardingjdbcUIDAO extends ShardingjdbcDAO implements IMemoryMetricUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMetricShardingjdbcUIDAO.class);
    private static final String GET_MEMORY_METRIC_SQL = "select * from {0} where {1} = ?";

    public MemoryMetricShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public Trend getHeapMemoryTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        return getMemoryTrend(instanceId, step, durationPoints, true);
    }

    @Override public Trend getNoHeapMemoryTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        return getMemoryTrend(instanceId, step, durationPoints, false);
    }

    private Trend getMemoryTrend(int instanceId, Step step, List<DurationPoint> durationPoints,
        boolean isHeap) {
        String tableName = TimePyramidTableNameBuilder.build(step, MemoryMetricTable.TABLE);

        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(GET_MEMORY_METRIC_SQL, tableName, MemoryMetricTable.ID.getName());

        Trend trend = new Trend();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + BooleanUtils.booleanToValue(isHeap);
            try (
                    ResultSet rs = client.executeQuery(sql, new String[] {id});
                    Statement statement = rs.getStatement();
                    Connection conn = statement.getConnection();
                ) {
                if (rs.next()) {
                    long max = rs.getLong(MemoryMetricTable.MAX.getName());
                    long used = rs.getLong(MemoryMetricTable.USED.getName());
                    long times = rs.getLong(MemoryMetricTable.TIMES.getName());
                    trend.getMetrics().add((int)(used / times));

                    if (max < 0) {
                        trend.getMaxMetrics().add((int)(used / times));
                    } else {
                        trend.getMaxMetrics().add((int)(max / times));
                    }
                } else {
                    trend.getMetrics().add(0);
                    trend.getMaxMetrics().add(0);
                }
            } catch (SQLException | ShardingjdbcClientException e) {
                logger.error(e.getMessage(), e);
            }
        });

        return trend;
    }
}
