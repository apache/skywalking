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
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentDurationUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDurationTable;
import org.apache.skywalking.apm.collector.storage.ui.trace.BasicTrace;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceBrief;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class SegmentDurationH2UIDAO extends H2DAO implements ISegmentDurationUIDAO {

    private final Logger logger = LoggerFactory.getLogger(SegmentDurationH2UIDAO.class);

    public SegmentDurationH2UIDAO(H2Client client) {
        super(client);
    }

    @Override
    public TraceBrief loadTop(long startSecondTimeBucket, long endSecondTimeBucket, long minDuration, long maxDuration,
        String operationName, int applicationId, int limit, int from, String... segmentIds) {
        H2Client client = getClient();
        String sql = "select * from {0} where {1} >= ? and {1} <= ?";
        List<Object> params = new ArrayList<>();
        List<Object> columns = new ArrayList<>();
        columns.add(SegmentDurationTable.TABLE);
        columns.add(SegmentDurationTable.COLUMN_TIME_BUCKET);
        params.add(startSecondTimeBucket);
        params.add(endSecondTimeBucket);
        int paramIndex = 1;
        if (minDuration != -1 || maxDuration != -1) {
            if (minDuration != -1) {
                paramIndex++;
                sql = sql + " and {" + paramIndex + "} >= ?";
                params.add(minDuration);
                columns.add(SegmentDurationTable.COLUMN_DURATION);
            }
            if (maxDuration != -1) {
                paramIndex++;
                sql = sql + " and {" + paramIndex + "} <= ?";
                params.add(maxDuration);
                columns.add(SegmentDurationTable.COLUMN_DURATION);
            }
        }
        if (StringUtils.isNotEmpty(operationName)) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} = ?";
            params.add(operationName);
            columns.add(SegmentDurationTable.COLUMN_SERVICE_NAME);
        }
        if (StringUtils.isNotEmpty(segmentIds)) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} = ?";
            params.add(segmentIds);
            columns.add(SegmentDurationTable.COLUMN_TRACE_ID);
        }
        if (applicationId != 0) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} = ?";
            params.add(applicationId);
            columns.add(SegmentDurationTable.COLUMN_APPLICATION_ID);
        }

        sql = sql + " limit " + from + "," + limit;
        sql = SqlBuilder.buildSql(sql, columns);
        Object[] p = params.toArray(new Object[0]);

        TraceBrief traceBrief = new TraceBrief();

        int cnt = 0;
        try (ResultSet rs = client.executeQuery(sql, p)) {
            while (rs.next()) {
                BasicTrace basicTrace = new BasicTrace();
                basicTrace.setSegmentId(rs.getString(SegmentDurationTable.COLUMN_SEGMENT_ID));
                basicTrace.setDuration(rs.getInt(SegmentDurationTable.COLUMN_DURATION));
                basicTrace.setStart(rs.getLong(SegmentDurationTable.COLUMN_START_TIME));
                basicTrace.setOperationName(rs.getString(SegmentDurationTable.COLUMN_SERVICE_NAME));
                basicTrace.setError(BooleanUtils.valueToBoolean(rs.getInt(SegmentDurationTable.COLUMN_IS_ERROR)));
                traceBrief.getTraces().add(basicTrace);
                cnt++;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        traceBrief.setTotal(cnt);
        return traceBrief;
    }
}
