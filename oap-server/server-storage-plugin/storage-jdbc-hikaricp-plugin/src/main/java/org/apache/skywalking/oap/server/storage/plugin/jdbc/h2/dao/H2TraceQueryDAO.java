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

import com.google.common.base.Strings;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.elasticsearch.search.sort.SortOrder;

public class H2TraceQueryDAO implements ITraceQueryDAO {
    private ModuleManager manager;
    private JDBCHikariCPClient h2Client;
    private List<String> searchableTagKeys;
    private int maxSizeOfArrayColumn;
    private int numOfSearchableValuesPerTag;

    public H2TraceQueryDAO(ModuleManager manager,
                           JDBCHikariCPClient h2Client,
                           final int maxSizeOfArrayColumn,
                           final int numOfSearchableValuesPerTag) {
        this.h2Client = h2Client;
        this.manager = manager;
        this.maxSizeOfArrayColumn = maxSizeOfArrayColumn;
        this.numOfSearchableValuesPerTag = numOfSearchableValuesPerTag;
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB,
                                       long endSecondTB,
                                       long minDuration,
                                       long maxDuration,
                                       String endpointName,
                                       String serviceId,
                                       String serviceInstanceId,
                                       String endpointId,
                                       String traceId,
                                       int limit,
                                       int from,
                                       TraceState traceState,
                                       QueryOrder queryOrder,
                                       final List<Tag> tags) throws IOException {
        if (searchableTagKeys == null) {
            final ConfigService configService = manager.find(CoreModule.NAME)
                                                       .provider()
                                                       .getService(ConfigService.class);
            searchableTagKeys = Arrays.asList(configService.getSearchableTracesTags().split(Const.COMMA));
            if (searchableTagKeys.size() > maxSizeOfArrayColumn) {
                this.searchableTagKeys = searchableTagKeys.subList(0, maxSizeOfArrayColumn);
            }
        }

        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>(10);

        sql.append("from ").append(SegmentRecord.INDEX_NAME).append(" where ");
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
        if (!Strings.isNullOrEmpty(endpointName)) {
            sql.append(" and ").append(SegmentRecord.ENDPOINT_NAME).append(" like concat('%',?,'%')");
            parameters.add(endpointName);
        }
        if (StringUtil.isNotEmpty(serviceId)) {
            sql.append(" and ").append(SegmentRecord.SERVICE_ID).append(" = ?");
            parameters.add(serviceId);
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            sql.append(" and ").append(SegmentRecord.SERVICE_INSTANCE_ID).append(" = ?");
            parameters.add(serviceInstanceId);
        }
        if (!Strings.isNullOrEmpty(endpointId)) {
            sql.append(" and ").append(SegmentRecord.ENDPOINT_ID).append(" = ?");
            parameters.add(endpointId);
        }
        if (!Strings.isNullOrEmpty(traceId)) {
            sql.append(" and ").append(SegmentRecord.TRACE_ID).append(" = ?");
            parameters.add(traceId);
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            for (final Tag tag : tags) {
                final int foundIdx = searchableTagKeys.indexOf(tag.getKey());
                if (foundIdx > -1) {
                    sql.append(" and (");
                    for (int i = 0; i < numOfSearchableValuesPerTag; i++) {
                        final String physicalColumn = SegmentRecord.TAGS + "_" + (foundIdx * numOfSearchableValuesPerTag + i);
                        sql.append(physicalColumn).append(" = ? ");
                        parameters.add(tag.toString());
                        if (i != numOfSearchableValuesPerTag - 1) {
                            sql.append(" or ");
                        }
                    }
                    sql.append(")");
                } else {
                    //If the tag is not searchable, but is required, then don't need to run the real query.
                    return new TraceBrief();
                }
            }
        }
        switch (traceState) {
            case ERROR:
                sql.append(" and ").append(SegmentRecord.IS_ERROR).append(" = ").append(BooleanUtils.TRUE);
                break;
            case SUCCESS:
                sql.append(" and ").append(SegmentRecord.IS_ERROR).append(" = ").append(BooleanUtils.FALSE);
                break;
        }
        switch (queryOrder) {
            case BY_START_TIME:
                sql.append(" order by ").append(SegmentRecord.START_TIME).append(" ").append(SortOrder.DESC);
                break;
            case BY_DURATION:
                sql.append(" order by ").append(SegmentRecord.LATENCY).append(" ").append(SortOrder.DESC);
                break;
        }

        TraceBrief traceBrief = new TraceBrief();
        try (Connection connection = h2Client.getConnection()) {

            try (ResultSet resultSet = h2Client.executeQuery(connection, buildCountStatement(sql.toString()), parameters
                .toArray(new Object[0]))) {
                while (resultSet.next()) {
                    traceBrief.setTotal(resultSet.getInt("total"));
                }
            }

            buildLimit(sql, from, limit);

            try (ResultSet resultSet = h2Client.executeQuery(
                connection, "select * " + sql.toString(), parameters.toArray(new Object[0]))) {
                while (resultSet.next()) {
                    BasicTrace basicTrace = new BasicTrace();

                    basicTrace.setSegmentId(resultSet.getString(SegmentRecord.SEGMENT_ID));
                    basicTrace.setStart(resultSet.getString(SegmentRecord.START_TIME));
                    basicTrace.getEndpointNames().add(resultSet.getString(SegmentRecord.ENDPOINT_NAME));
                    basicTrace.setDuration(resultSet.getInt(SegmentRecord.LATENCY));
                    basicTrace.setError(BooleanUtils.valueToBoolean(resultSet.getInt(SegmentRecord.IS_ERROR)));
                    String traceIds = resultSet.getString(SegmentRecord.TRACE_ID);
                    basicTrace.getTraceIds().add(traceIds);
                    traceBrief.getTraces().add(basicTrace);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        return traceBrief;
    }

    protected String buildCountStatement(String sql) {
        return "select count(1) total from (select 1 " + sql + " )";
    }

    protected void buildLimit(StringBuilder sql, int from, int limit) {
        sql.append(" LIMIT ").append(limit);
        sql.append(" OFFSET ").append(from);
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        List<SegmentRecord> segmentRecords = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {

            try (ResultSet resultSet = h2Client.executeQuery(
                connection, "select * from " + SegmentRecord.INDEX_NAME + " where " + SegmentRecord.TRACE_ID + " = ?",
                traceId
            )) {
                while (resultSet.next()) {
                    SegmentRecord segmentRecord = new SegmentRecord();
                    segmentRecord.setSegmentId(resultSet.getString(SegmentRecord.SEGMENT_ID));
                    segmentRecord.setTraceId(resultSet.getString(SegmentRecord.TRACE_ID));
                    segmentRecord.setServiceId(resultSet.getString(SegmentRecord.SERVICE_ID));
                    segmentRecord.setServiceInstanceId(resultSet.getString(SegmentRecord.SERVICE_INSTANCE_ID));
                    segmentRecord.setEndpointName(resultSet.getString(SegmentRecord.ENDPOINT_NAME));
                    segmentRecord.setStartTime(resultSet.getLong(SegmentRecord.START_TIME));
                    segmentRecord.setEndTime(resultSet.getLong(SegmentRecord.END_TIME));
                    segmentRecord.setLatency(resultSet.getInt(SegmentRecord.LATENCY));
                    segmentRecord.setIsError(resultSet.getInt(SegmentRecord.IS_ERROR));
                    String dataBinaryBase64 = resultSet.getString(SegmentRecord.DATA_BINARY);
                    if (!Strings.isNullOrEmpty(dataBinaryBase64)) {
                        segmentRecord.setDataBinary(Base64.getDecoder().decode(dataBinaryBase64));
                    }
                    segmentRecord.setVersion(resultSet.getInt(SegmentRecord.VERSION));
                    segmentRecords.add(segmentRecord);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return segmentRecords;
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) {
        return Collections.emptyList();
    }
}
