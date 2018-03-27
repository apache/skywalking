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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IMemoryPoolMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author clevertension
 */
public class MemoryPoolMetricH2UIDAO extends H2DAO implements IMemoryPoolMetricUIDAO {

    private final Logger logger = LoggerFactory.getLogger(MemoryPoolMetricH2UIDAO.class);
    private static final String GET_MEMORY_POOL_METRIC_SQL = "select * from {0} where {1} = ?";

    public MemoryPoolMetricH2UIDAO(H2Client client) {
        super(client);
    }

    @Override public JsonObject getMetric(int instanceId, long timeBucket, int poolType) {
        H2Client client = getClient();
        String id = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + poolType;
        String sql = SqlBuilder.buildSql(GET_MEMORY_POOL_METRIC_SQL, MemoryPoolMetricTable.TABLE, MemoryPoolMetricTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        JsonObject metric = new JsonObject();
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                metric.addProperty("max", rs.getInt(MemoryPoolMetricTable.COLUMN_MAX));
                metric.addProperty("init", rs.getInt(MemoryPoolMetricTable.COLUMN_INIT));
                metric.addProperty("used", rs.getInt(MemoryPoolMetricTable.COLUMN_USED));
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

    @Override public JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket, int poolType) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_MEMORY_POOL_METRIC_SQL, MemoryPoolMetricTable.TABLE, MemoryPoolMetricTable.COLUMN_ID);
        List<String> idList = new ArrayList<>();
        long timeBucket = startTimeBucket;
        do {
//            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND, timeBucket, 1);
            String id = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + poolType;
            idList.add(id);
        }
        while (timeBucket <= endTimeBucket);

        JsonObject metric = new JsonObject();
        JsonArray usedMetric = new JsonArray();

        idList.forEach(id -> {
            try (ResultSet rs = client.executeQuery(sql, new String[] {id})) {
                if (rs.next()) {
                    metric.addProperty("max", rs.getLong(MemoryPoolMetricTable.COLUMN_MAX));
                    metric.addProperty("init", rs.getLong(MemoryPoolMetricTable.COLUMN_INIT));
                    usedMetric.add(rs.getLong(MemoryPoolMetricTable.COLUMN_USED));
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
