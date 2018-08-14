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
import java.util.List;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IMemoryMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author clevertension
 */
public class MemoryMetricH2UIDAO extends H2DAO implements IMemoryMetricUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMetricH2UIDAO.class);
    private static final String GET_MEMORY_METRIC_SQL = "select * from {0} where {1} =?";

    public MemoryMetricH2UIDAO(H2Client client) {
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

        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_MEMORY_METRIC_SQL, tableName, MemoryMetricTable.ID.getName());

        Trend trend = new Trend();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + BooleanUtils.booleanToValue(isHeap);
            try (ResultSet rs = client.executeQuery(sql, new String[] {id})) {
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
            } catch (SQLException | H2ClientException e) {
                logger.error(e.getMessage(), e);
            }
        });

        return trend;
    }
}
