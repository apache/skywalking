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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.SearchableTracesTagsWatcher;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toSet;

@Slf4j
@RequiredArgsConstructor
public class JDBCTraceQueryDAO implements ITraceQueryDAO {
    private final ModuleManager manager;
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    private SearchableTracesTagsWatcher searchableTagKeys;

    private static String DETAIL_SELECT_QUERY = "select " + SegmentRecord.SEGMENT_ID + ", " +
        SegmentRecord.TRACE_ID + ", " +
        SegmentRecord.ENDPOINT_ID + ", " +
        SegmentRecord.SERVICE_ID + ", " +
        SegmentRecord.SERVICE_INSTANCE_ID + ", " +
        SegmentRecord.START_TIME + ", " +
        SegmentRecord.LATENCY + ", " +
        SegmentRecord.IS_ERROR + ", " +
        SegmentRecord.DATA_BINARY;

    @Override
    @SneakyThrows
    public TraceBrief queryBasicTraces(Duration duration,
                                       long minDuration,
                                       long maxDuration,
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
            searchableTagKeys = configService.getSearchableTracesTags();
        }
        if (tags != null && !searchableTagKeys.getSearchableTags().containsAll(tags.stream().map(Tag::getKey).collect(toSet()))) {
            log.warn(
                "Searching tags that are not searchable: {}",
                tags.stream().map(Tag::getKey).filter(not(searchableTagKeys.getSearchableTags()::contains)).collect(toSet()));
            return new TraceBrief();
        }

        long startSecondTB = 0;
        long endSecondTB = 0;
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
        }

        final var tables = startSecondTB > 0 && endSecondTB > 0 ?
            tableHelper.getTablesForRead(SegmentRecord.INDEX_NAME, startSecondTB, endSecondTB) :
            tableHelper.getTablesWithinTTL(SegmentRecord.INDEX_NAME);
        final var traces = new ArrayList<BasicTrace>();

        for (String table : tables) {
            StringBuilder sql = new StringBuilder();
            List<Object> parameters = new ArrayList<>(10);

            sql.append("from ").append(table);

            /*
             * This is an AdditionalEntity feature, see:
             * {@link org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase.AdditionalEntity}
             */
            final var timeBucket = TableHelper.getTimeBucket(table);
            final var tagTable = TableHelper.getTable(SegmentRecord.ADDITIONAL_TAG_TABLE, timeBucket);
            if (!CollectionUtils.isEmpty(tags)) {
                for (int i = 0; i < tags.size(); i++) {
                    sql.append(" inner join ").append(tagTable).append(" ");
                    sql.append(tagTable + i);
                    sql.append(" on ").append(table).append(".").append(JDBCTableInstaller.ID_COLUMN).append(" = ");
                    sql.append(tagTable + i).append(".").append(JDBCTableInstaller.ID_COLUMN);
                }
            }
            sql.append(" where ");
            sql.append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            parameters.add(SegmentRecord.INDEX_NAME);
            if (startSecondTB != 0 && endSecondTB != 0) {
                sql.append(" and ").append(table).append(".").append(SegmentRecord.TIME_BUCKET).append(" >= ?");
                parameters.add(startSecondTB);
                sql.append(" and ").append(table).append(".").append(SegmentRecord.TIME_BUCKET).append(" <= ?");
                parameters.add(endSecondTB);
            }
            if (minDuration != 0) {
                sql.append(" and ").append(SegmentRecord.LATENCY).append(" >= ?");
                parameters.add(minDuration);
            }
            if (maxDuration != 0) {
                sql.append(" and ").append(SegmentRecord.LATENCY).append(" <= ?");
                parameters.add(maxDuration);
            }
            if (StringUtil.isNotEmpty(serviceId)) {
                sql.append(" and ").append(table).append(".").append(SegmentRecord.SERVICE_ID).append(" = ?");
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
                for (int i = 0; i < tags.size(); i++) {
                    sql.append(" and ").append(tagTable + i).append(".");
                    sql.append(SegmentRecord.TAGS).append(" = ?");
                    parameters.add(tags.get(i).toString());
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
                    sql.append(" order by ").append(SegmentRecord.START_TIME).append(" ").append("desc");
                    break;
                case BY_DURATION:
                    sql.append(" order by ").append(SegmentRecord.LATENCY).append(" ").append("desc");
                    break;
            }

            buildLimit(sql, from, limit);

            jdbcClient.executeQuery(
                "select " +
                    SegmentRecord.SEGMENT_ID + ", " +
                    SegmentRecord.START_TIME + ", " +
                    SegmentRecord.ENDPOINT_ID + ", " +
                    SegmentRecord.LATENCY + ", " +
                    SegmentRecord.IS_ERROR + ", " +
                    SegmentRecord.TRACE_ID + " " + sql,
                resultSet -> {
                    while (resultSet.next()) {
                        BasicTrace basicTrace = new BasicTrace();

                        basicTrace.setSegmentId(resultSet.getString(SegmentRecord.SEGMENT_ID));
                        basicTrace.setStart(resultSet.getString(SegmentRecord.START_TIME));
                        basicTrace.getEndpointNames().add(
                            IDManager.EndpointID.analysisId(resultSet.getString(SegmentRecord.ENDPOINT_ID))
                                                .getEndpointName()
                        );
                        basicTrace.setDuration(resultSet.getInt(SegmentRecord.LATENCY));
                        basicTrace.setError(BooleanUtils.valueToBoolean(resultSet.getInt(SegmentRecord.IS_ERROR)));
                        String traceIds = resultSet.getString(SegmentRecord.TRACE_ID);
                        basicTrace.getTraceIds().add(traceIds);
                        traces.add(basicTrace);
                    }
                    return null;
                },
                parameters.toArray(new Object[0]));
        }

        return new TraceBrief(traces); // TODO: sort,
    }

    protected void buildLimit(StringBuilder sql, int from, int limit) {
        sql.append(" LIMIT ").append(limit);
        sql.append(" OFFSET ").append(from);
    }

    @Override
    @SneakyThrows
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        final var tables = tableHelper.getTablesWithinTTL(SegmentRecord.INDEX_NAME);
        final var segmentRecords = new ArrayList<SegmentRecord>();

        for (String table : tables) {
            jdbcClient.executeQuery(
                DETAIL_SELECT_QUERY + " from " + table + " where " +
                    JDBCTableInstaller.TABLE_COLUMN + " = ? and " +
                    SegmentRecord.TRACE_ID + " = ?",
                resultSet -> {
                    segmentRecords.addAll(buildRecords(resultSet));
                    return null;
                },
                SegmentRecord.INDEX_NAME, traceId
            );
        }
        return segmentRecords;
    }

    @SneakyThrows
    @Override
    public List<SegmentRecord> queryBySegmentIdList(List<String> segmentIdList) throws IOException {
        final var tables = tableHelper.getTablesWithinTTL(SegmentRecord.INDEX_NAME);
        final var segmentRecords = new ArrayList<SegmentRecord>();
        final ArrayList<String> conditions = new ArrayList<>();
        conditions.add(SegmentRecord.INDEX_NAME);
        conditions.addAll(segmentIdList);

        for (String table : tables) {
            jdbcClient.executeQuery(
                DETAIL_SELECT_QUERY + " from " + table + " where " +
                    JDBCTableInstaller.TABLE_COLUMN + " = ? and " +
                    SegmentRecord.SEGMENT_ID + " in " +
                    segmentIdList.stream().map(it -> "?").collect(Collectors.joining(",", "(", ")")),
                resultSet -> {
                    segmentRecords.addAll(buildRecords(resultSet));
                    return null;
                },
                conditions.toArray()
            );
        }
        return segmentRecords;
    }

    @SneakyThrows
    @Override
    public List<SegmentRecord> queryByTraceIdWithInstanceId(List<String> traceIdList, List<String> instanceIdList) throws IOException {
        final var tables = tableHelper.getTablesWithinTTL(SegmentRecord.INDEX_NAME);
        final var segmentRecords = new ArrayList<SegmentRecord>();
        final ArrayList<String> conditions = new ArrayList<>();
        conditions.add(SegmentRecord.INDEX_NAME);
        conditions.addAll(traceIdList);
        conditions.addAll(instanceIdList);

        for (String table : tables) {
            jdbcClient.executeQuery(
                DETAIL_SELECT_QUERY + " from " + table + " where " +
                    JDBCTableInstaller.TABLE_COLUMN + " = ? and " +
                    SegmentRecord.TRACE_ID + " in " +
                    traceIdList.stream().map(it -> "?").collect(Collectors.joining(",", "(", ") and ")) +
                    SegmentRecord.SERVICE_INSTANCE_ID + " in " +
                    instanceIdList.stream().map(it -> "?").collect(Collectors.joining(",", "(", ")")),
                resultSet -> {
                    segmentRecords.addAll(buildRecords(resultSet));
                    return null;
                },
                conditions.toArray()
            );
        }
        return segmentRecords;
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) {
        return Collections.emptyList();
    }

    private List<SegmentRecord> buildRecords(ResultSet resultSet) throws SQLException {
        final List<SegmentRecord> records = new ArrayList<>();
        while (resultSet.next()) {
            SegmentRecord segmentRecord = new SegmentRecord();
            segmentRecord.setSegmentId(resultSet.getString(SegmentRecord.SEGMENT_ID));
            segmentRecord.setTraceId(resultSet.getString(SegmentRecord.TRACE_ID));
            segmentRecord.setEndpointId(resultSet.getString(SegmentRecord.ENDPOINT_ID));
            segmentRecord.setServiceId(resultSet.getString(SegmentRecord.SERVICE_ID));
            segmentRecord.setServiceInstanceId(resultSet.getString(SegmentRecord.SERVICE_INSTANCE_ID));
            segmentRecord.setStartTime(resultSet.getLong(SegmentRecord.START_TIME));
            segmentRecord.setLatency(resultSet.getInt(SegmentRecord.LATENCY));
            segmentRecord.setIsError(resultSet.getInt(SegmentRecord.IS_ERROR));
            String dataBinaryBase64 = resultSet.getString(SegmentRecord.DATA_BINARY);
            if (!Strings.isNullOrEmpty(dataBinaryBase64)) {
                segmentRecord.setDataBinary(Base64.getDecoder().decode(dataBinaryBase64));
            }
            records.add(segmentRecord);
        }
        return records;
    }
}
