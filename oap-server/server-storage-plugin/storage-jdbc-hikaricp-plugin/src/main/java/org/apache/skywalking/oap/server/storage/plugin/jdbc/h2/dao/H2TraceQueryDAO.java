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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.entity.QueryOrder;
import org.apache.skywalking.oap.server.core.query.entity.TraceBrief;
import org.apache.skywalking.oap.server.core.query.entity.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.StringUtils;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * @author wusheng
 */
public class H2TraceQueryDAO implements ITraceQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2TraceQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB, long endSecondTB, long minDuration, long maxDuration,
        String endpointName, int serviceId, int endpointId, String traceId, int limit, int from, TraceState traceState,
        QueryOrder queryOrder) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>(10);

        sql.append(" 1=1 ");
        if (startSecondTB != 0 && endSecondTB != 0) {
            sql.append(" and ").append(SegmentRecord.TIME_BUCKET).append(" >= ?");
            parameters.add(startSecondTB);
            sql.append(" and ").append(SegmentRecord.TIME_BUCKET).append(" <= ?");
            parameters.add(endSecondTB);
        }
        if (minDuration != 0 || maxDuration != 0) {
            if (minDuration != 0) {
                sql.append(" and ").append(SegmentRecord.LATENCY).append(" >= ?");
                parameters.add(minDuration);
            }
            if (maxDuration != 0) {
                sql.append(" and ").append(SegmentRecord.LATENCY).append(" <= ?");
                parameters.add(maxDuration);
            }
        }
        if (StringUtils.isNotEmpty(endpointName)) {
            sql.append(" and ").append(SegmentRecord.ENDPOINT_NAME).append(" like '%" + endpointName + "%'");
        }
        if (serviceId != 0) {
            sql.append(" and ").append(SegmentRecord.SERVICE_ID).append(" = ?");
            parameters.add(serviceId);
        }

        return null;
    }

    @Override public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        return null;
    }
}
