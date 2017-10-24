/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.jvm.MemoryMetricTable;
import org.skywalking.apm.collector.storage.h2.SqlBuilder;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author clevertension
 */
public class MemoryMetricH2DAO extends H2DAO implements IMemoryMetricDAO {
    private final Logger logger = LoggerFactory.getLogger(InstanceH2DAO.class);
    private static final String GET_MEMORY_METRIC_SQL = "select * from {0} where {1} =?";

    @Override public JsonObject getMetric(int instanceId, long timeBucket, boolean isHeap) {
        H2Client client = getClient();
        String id = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + isHeap;
        String sql = SqlBuilder.buildSql(GET_MEMORY_METRIC_SQL, MemoryMetricTable.TABLE, MemoryMetricTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        JsonObject metric = new JsonObject();
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                metric.addProperty("max", rs.getInt(MemoryMetricTable.COLUMN_MAX));
                metric.addProperty("init", rs.getInt(MemoryMetricTable.COLUMN_INIT));
                metric.addProperty("used", rs.getInt(MemoryMetricTable.COLUMN_USED));
            } else {
                metric.addProperty("max", 0);
                metric.addProperty("init", 0);
                metric.addProperty("used", 0);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return metric;
    }

    @Override public JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket, boolean isHeap) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_MEMORY_METRIC_SQL, MemoryMetricTable.TABLE, MemoryMetricTable.COLUMN_ID);
        List<String> idList = new ArrayList<>();
        long timeBucket = startTimeBucket;
        do {
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, 1);
            String id = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + isHeap;
            idList.add(id);
        }
        while (timeBucket <= endTimeBucket);

        JsonObject metric = new JsonObject();
        JsonArray usedMetric = new JsonArray();

        idList.forEach(id -> {
            try (ResultSet rs = client.executeQuery(sql, new String[] {id})) {
                if (rs.next()) {
                    metric.addProperty("max", rs.getLong(MemoryMetricTable.COLUMN_MAX));
                    metric.addProperty("init", rs.getLong(MemoryMetricTable.COLUMN_INIT));
                    usedMetric.add(rs.getLong(MemoryMetricTable.COLUMN_USED));
                } else {
                    metric.addProperty("max", 0);
                    metric.addProperty("init", 0);
                    usedMetric.add(0);
                }
            } catch (SQLException | H2ClientException e) {
                logger.error(e.getMessage(), e);
            }
        });

        metric.add("used", usedMetric);
        return metric;
    }
}
