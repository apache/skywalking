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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.jvm.GCMetricTable;
import org.skywalking.apm.collector.storage.h2.SqlBuilder;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.network.proto.GCPhrase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * @author pengys5, clevertension
 */
public class GCMetricH2DAO extends H2DAO implements IGCMetricDAO {
    private final Logger logger = LoggerFactory.getLogger(GCMetricH2DAO.class);
    private static final String GET_GC_COUNT_SQL = "select {1}, sum({0}) as cnt, {1} from {2} where {3} = ? and {4} in (";
    private static final String GET_GC_METRIC_SQL = "select * from {0} where {1} = ?";
    private static final String GET_GC_METRICS_SQL = "select * from {0} where {1} in (";
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
                GCMetricTable.TABLE, GCMetricTable.COLUMN_INSTANCE_ID, "id");
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
        String sql = SqlBuilder.buildSql(GET_GC_METRIC_SQL, GCMetricTable.TABLE, "id");
        String youngId = timeBucket + Const.ID_SPLIT + GCPhrase.NEW_VALUE + instanceId;
        Object[] params = new Object[]{youngId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                response.addProperty("ygc", rs.getInt(GCMetricTable.COLUMN_COUNT));
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        String oldId = timeBucket + Const.ID_SPLIT + GCPhrase.OLD_VALUE + instanceId;
        Object[] params1 = new Object[]{oldId};
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
        String sql = SqlBuilder.buildSql(GET_GC_METRICS_SQL, GCMetricTable.TABLE, "id");
        long timeBucket = startTimeBucket;
        List<String> idList = new ArrayList<>();
        do {
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, 1);
            String youngId = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + GCPhrase.NEW_VALUE;
            idList.add(youngId);
        }
        while (timeBucket <= endTimeBucket);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < idList.size(); i++) {
            builder.append("?,");
        }
        builder.delete(builder.length() - 1, builder.length());
        builder.append(")");
        sql = sql + builder;
        Object[] params = idList.toArray(new String[0]);

        JsonArray youngArray = new JsonArray();
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                youngArray.add(rs.getInt(GCMetricTable.COLUMN_COUNT));
            }
            if (youngArray.size() == 0) {
                youngArray.add(0);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        response.add("ygc", youngArray);
        List<String> idList1 = new ArrayList<>();
        timeBucket = startTimeBucket;
        do {
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, 1);
            String oldId = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + GCPhrase.OLD_VALUE;
            idList1.add(oldId);
        }
        while (timeBucket <= endTimeBucket);
        String sql1 = SqlBuilder.buildSql(GET_GC_METRICS_SQL, GCMetricTable.TABLE, "id");
        StringBuilder builder1 = new StringBuilder();
        for (int i = 0; i < idList1.size(); i++) {
            builder1.append("?,");
        }
        builder1.delete(builder1.length() - 1, builder1.length());
        builder1.append(")");
        sql1 = sql1 + builder1;
        Object[] params1 = idList.toArray(new String[0]);
        JsonArray oldArray = new JsonArray();

        try (ResultSet rs = client.executeQuery(sql1, params1)) {
            while (rs.next()) {
                oldArray.add(rs.getInt(GCMetricTable.COLUMN_COUNT));
            }
            if (oldArray.size() == 0) {
                oldArray.add(0);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        response.add("ogc", oldArray);

        return response;
    }
}
