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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.Gson;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentDurationUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDurationTable;
import org.apache.skywalking.apm.collector.storage.ui.trace.BasicTrace;
import org.apache.skywalking.apm.collector.storage.ui.trace.QueryOrder;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceBrief;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class SegmentDurationShardingjdbcUIDAO extends ShardingjdbcDAO implements ISegmentDurationUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(SegmentDurationShardingjdbcUIDAO.class);

    private final Gson gson = new Gson();

    public SegmentDurationShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override
    public TraceBrief loadTop(long startSecondTimeBucket, long endSecondTimeBucket, long minDuration, long maxDuration,
        String operationName, int applicationId, int limit, int from, TraceState traceState, QueryOrder queryOrder, String... segmentIds) {
        ShardingjdbcClient client = getClient();
        String sql = "select * from {0} where {1} >= ? and {1} <= ?";
        List<Object> params = new ArrayList<>();
        List<Object> columns = new ArrayList<>();
        columns.add(SegmentDurationTable.TABLE);
        columns.add(SegmentDurationTable.TIME_BUCKET.getName());
        params.add(startSecondTimeBucket);
        params.add(endSecondTimeBucket);
        int paramIndex = 1;
        if (minDuration != -1 || maxDuration != -1) {
            if (minDuration != -1) {
                paramIndex++;
                sql = sql + " and {" + paramIndex + "} >= ?";
                params.add(minDuration);
                columns.add(SegmentDurationTable.DURATION.getName());
            }
            if (maxDuration != -1) {
                paramIndex++;
                sql = sql + " and {" + paramIndex + "} <= ?";
                params.add(maxDuration);
                columns.add(SegmentDurationTable.DURATION.getName());
            }
        }
        if (StringUtils.isNotEmpty(operationName)) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} like ?";
            params.add("%" + operationName + "%");
            columns.add(SegmentDurationTable.SERVICE_NAME.getName());
        }
        if (StringUtils.isNotEmpty(segmentIds)) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} in (?)";
            String segmentIdsParam = Arrays.toString(segmentIds).replace("[", "").replace("]", "");
            params.add(segmentIdsParam);
            columns.add(SegmentDurationTable.SEGMENT_ID.getName());
        }
        if (applicationId != 0) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} = ?";
            params.add(applicationId);
            columns.add(SegmentDurationTable.APPLICATION_ID.getName());
        }
        switch (traceState) {
            case ERROR:
                paramIndex++;
                sql = sql + " and {" + paramIndex + "} = ?";
                params.add(BooleanUtils.TRUE);
                columns.add(SegmentDurationTable.IS_ERROR.getName());
                break;
            case SUCCESS:
                paramIndex++;
                sql = sql + " and {" + paramIndex + "} = ?";
                params.add(BooleanUtils.FALSE);
                columns.add(SegmentDurationTable.IS_ERROR.getName());
                break;
        }
        switch (queryOrder) {
            case BY_START_TIME:
                paramIndex++;
                sql = sql + " order by {" + paramIndex + "} desc";
                columns.add(SegmentDurationTable.START_TIME.getName());
                break;
            case BY_DURATION:
                paramIndex++;
                sql = sql + " order by {" + paramIndex + "} desc";
                columns.add(SegmentDurationTable.DURATION.getName());
                break;
        }

        sql = sql + " limit " + from + "," + limit;
        sql = SqlBuilder.buildSql(sql, columns);
        Object[] p = params.toArray(new Object[0]);

        TraceBrief traceBrief = new TraceBrief();

        int cnt = 0;
        try (
                ResultSet rs = client.executeQuery(sql, p);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                BasicTrace basicTrace = new BasicTrace();
                basicTrace.setSegmentId(rs.getString(SegmentDurationTable.SEGMENT_ID.getName()));
                basicTrace.setDuration(rs.getInt(SegmentDurationTable.DURATION.getName()));
                basicTrace.setStart(rs.getLong(SegmentDurationTable.START_TIME.getName()));
                String serviceNameJsonStr = rs.getString(SegmentDurationTable.SERVICE_NAME.getName());
                if (StringUtils.isNotEmpty(serviceNameJsonStr)) {
                    List serviceNames = gson.fromJson(serviceNameJsonStr, LinkedList.class);
                    basicTrace.getOperationName().addAll(serviceNames);
                }
                basicTrace.setError(BooleanUtils.valueToBoolean(rs.getInt(SegmentDurationTable.IS_ERROR.getName())));
                traceBrief.getTraces().add(basicTrace);
                cnt++;
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        traceBrief.setTotal(cnt);
        return traceBrief;
    }
}
