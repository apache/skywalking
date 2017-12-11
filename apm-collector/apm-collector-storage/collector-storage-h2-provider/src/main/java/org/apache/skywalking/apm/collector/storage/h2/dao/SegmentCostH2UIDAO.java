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
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostUIDAO;
import org.elasticsearch.search.sort.SortOrder;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentCostTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class SegmentCostH2UIDAO extends H2DAO implements ISegmentCostUIDAO {

    private final Logger logger = LoggerFactory.getLogger(SegmentCostH2UIDAO.class);
    private static final String GET_SEGMENT_COST_SQL = "select * from {0} where {1} >= ? and {1} <= ?";

    public SegmentCostH2UIDAO(H2Client client) {
        super(client);
    }

    @Override public JsonObject loadTop(long startTime, long endTime, long minCost, long maxCost, String operationName,
        Error error, int applicationId, List<String> segmentIds, int limit, int from, Sort sort) {
        H2Client client = getClient();
        String sql = GET_SEGMENT_COST_SQL;
        List<Object> params = new ArrayList<>();
        List<Object> columns = new ArrayList<>();
        columns.add(SegmentCostTable.TABLE);
        columns.add(SegmentCostTable.COLUMN_TIME_BUCKET);
        params.add(startTime);
        params.add(endTime);
        int paramIndex = 1;
        if (minCost != -1 || maxCost != -1) {
            if (minCost != -1) {
                paramIndex++;
                sql = sql + " and {" + paramIndex + "} >= ?";
                params.add(minCost);
                columns.add(SegmentCostTable.COLUMN_COST);
            }
            if (maxCost != -1) {
                paramIndex++;
                sql = sql + " and {" + paramIndex + "} <= ?";
                params.add(maxCost);
                columns.add(SegmentCostTable.COLUMN_COST);
            }
        }
        if (StringUtils.isNotEmpty(operationName)) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} = ?";
            params.add(operationName);
            columns.add(SegmentCostTable.COLUMN_SERVICE_NAME);
        }
        if (CollectionUtils.isNotEmpty(segmentIds)) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} in (";
            columns.add(SegmentCostTable.COLUMN_SEGMENT_ID);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < segmentIds.size(); i++) {
                builder.append("?,");
            }
            builder.delete(builder.length() - 1, builder.length());
            builder.append(")");
            sql = sql + builder;
            for (String segmentId : segmentIds) {
                params.add(segmentId);
            }
        }
        if (Error.True.equals(error)) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} = ?";
            params.add(true);
            columns.add(SegmentCostTable.COLUMN_IS_ERROR);
        } else if (Error.False.equals(error)) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} = ?";
            params.add(false);
            columns.add(SegmentCostTable.COLUMN_IS_ERROR);
        }
        if (applicationId != 0) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} = ?";
            params.add(applicationId);
            columns.add(SegmentCostTable.COLUMN_APPLICATION_ID);
        }

        if (Sort.Cost.equals(sort)) {
            sql = sql + " order by " + SegmentCostTable.COLUMN_COST + " " + SortOrder.DESC;
        } else if (Sort.Time.equals(sort)) {
            sql = sql + " order by " + SegmentCostTable.COLUMN_START_TIME + " " + SortOrder.DESC;
        }

        sql = sql + " limit " + from + "," + limit;
        sql = SqlBuilder.buildSql(sql, columns);
        Object[] p = params.toArray(new Object[0]);

        JsonObject topSegPaging = new JsonObject();

        JsonArray topSegArray = new JsonArray();
        topSegPaging.add("data", topSegArray);
        int cnt = 0;
        int num = from;
        try (ResultSet rs = client.executeQuery(sql, p)) {
            while (rs.next()) {
                JsonObject topSegmentJson = new JsonObject();
                topSegmentJson.addProperty("num", num);
                String segmentId = rs.getString(SegmentCostTable.COLUMN_SEGMENT_ID);
                topSegmentJson.addProperty(SegmentCostTable.COLUMN_SEGMENT_ID, segmentId);
                topSegmentJson.addProperty(SegmentCostTable.COLUMN_START_TIME, rs.getLong(SegmentCostTable.COLUMN_START_TIME));
                topSegmentJson.addProperty(SegmentCostTable.COLUMN_END_TIME, rs.getLong(SegmentCostTable.COLUMN_END_TIME));
                topSegmentJson.addProperty(SegmentCostTable.COLUMN_APPLICATION_ID, rs.getInt(SegmentCostTable.COLUMN_APPLICATION_ID));
                topSegmentJson.addProperty(SegmentCostTable.COLUMN_SERVICE_NAME, rs.getString(SegmentCostTable.COLUMN_SERVICE_NAME));
                topSegmentJson.addProperty(SegmentCostTable.COLUMN_COST, rs.getLong(SegmentCostTable.COLUMN_COST));
                topSegmentJson.addProperty(SegmentCostTable.COLUMN_IS_ERROR, rs.getBoolean(SegmentCostTable.COLUMN_IS_ERROR));

                num++;
                topSegArray.add(topSegmentJson);
                cnt++;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        topSegPaging.addProperty("recordsTotal", cnt);
        return topSegPaging;
    }
}
