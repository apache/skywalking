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


package org.apache.skywalking.apm.collector.storage.h2.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.IGCMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetricTable;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.network.proto.GCPhrase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class GCMetricH2UIDAO extends H2DAO implements IGCMetricUIDAO {

    private final Logger logger = LoggerFactory.getLogger(GCMetricH2UIDAO.class);
    private static final String GET_GC_COUNT_SQL = "select {1}, sum({0}) as cnt, {1} from {2} where {3} = ? and {4} in (";
    private static final String GET_GC_METRIC_SQL = "select * from {0} where {1} = ?";

    public GCMetricH2UIDAO(H2Client client) {
        super(client);
    }

    @Override public GCCount getGCCount(long[] timeBuckets, int instanceId) {
        GCCount gcCount = new GCCount();
        H2Client client = getClient();
        String sql = GET_GC_COUNT_SQL;
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < timeBuckets.length; i++) {
            builder.append("?,");
        }
        builder.delete(builder.length() - 1, builder.length());
        builder.append(")");
        sql = sql + builder + " group by {1}";
        sql = SqlBuilder.buildSql(sql, GCMetricTable.COLUMN_COUNT, GCMetricTable.COLUMN_PHRASE,
            GCMetricTable.TABLE, GCMetricTable.COLUMN_INSTANCE_ID, GCMetricTable.COLUMN_ID);
        Object[] params = new Object[timeBuckets.length + 1];
        for (int i = 0; i < timeBuckets.length; i++) {
            params[i + 1] = timeBuckets[i];
        }
        params[0] = instanceId;
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                int phrase = rs.getInt(GCMetricTable.COLUMN_PHRASE);
                int count = rs.getInt("cnt");

                if (phrase == GCPhrase.NEW_VALUE) {
                    gcCount.setYoung(count);
                } else if (phrase == GCPhrase.OLD_VALUE) {
                    gcCount.setOld(count);
                }
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return gcCount;
    }

    @Override public JsonObject getMetric(int instanceId, long timeBucket) {
        JsonObject response = new JsonObject();
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_GC_METRIC_SQL, GCMetricTable.TABLE, GCMetricTable.COLUMN_ID);
        String youngId = timeBucket + Const.ID_SPLIT + GCPhrase.NEW_VALUE + instanceId;
        Object[] params = new Object[] {youngId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                response.addProperty("ygc", rs.getInt(GCMetricTable.COLUMN_COUNT));
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        String oldId = timeBucket + Const.ID_SPLIT + GCPhrase.OLD_VALUE + instanceId;
        Object[] params1 = new Object[] {oldId};
        try (ResultSet rs = client.executeQuery(sql, params1)) {
            if (rs.next()) {
                response.addProperty("ogc", rs.getInt(GCMetricTable.COLUMN_COUNT));
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }

        return response;
    }

    @Override public JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket) {
        JsonObject response = new JsonObject();
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_GC_METRIC_SQL, GCMetricTable.TABLE, GCMetricTable.COLUMN_ID);
        long timeBucket = startTimeBucket;
        List<String> youngIdsList = new ArrayList<>();
        do {
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, 1);
            String youngId = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + GCPhrase.NEW_VALUE;
            youngIdsList.add(youngId);
        }
        while (timeBucket <= endTimeBucket);

        JsonArray youngArray = new JsonArray();
        forEachRs(client, youngIdsList, sql, youngArray);
        response.add("ygc", youngArray);

        List<String> oldIdsList = new ArrayList<>();
        timeBucket = startTimeBucket;
        do {
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, 1);
            String oldId = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + GCPhrase.OLD_VALUE;
            oldIdsList.add(oldId);
        }
        while (timeBucket <= endTimeBucket);

        JsonArray oldArray = new JsonArray();
        forEachRs(client, oldIdsList, sql, oldArray);
        response.add("ogc", oldArray);

        return response;
    }

    private void forEachRs(H2Client client, List<String> idsList, String sql, JsonArray metricArray) {
        idsList.forEach(id -> {
            try (ResultSet rs = client.executeQuery(sql, new String[] {id})) {
                if (rs.next()) {
                    metricArray.add(rs.getInt(GCMetricTable.COLUMN_COUNT));
                } else {
                    metricArray.add(0);
                }
            } catch (SQLException | H2ClientException e) {
                logger.error(e.getMessage(), e);
            }
        });
    }
}
