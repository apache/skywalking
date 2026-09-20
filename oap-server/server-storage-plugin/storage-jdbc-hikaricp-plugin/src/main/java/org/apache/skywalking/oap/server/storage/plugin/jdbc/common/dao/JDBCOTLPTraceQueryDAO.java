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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceRelationTraffic;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceSpanTraffic;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceTraffic;
import org.apache.skywalking.oap.server.core.otlp.OTLPSpanRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.OTLPTraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.storage.query.IOTLPTraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

/**
 * Reads {@code otlp_span} on the JDBC storages. A tag condition is one inner join on the {@code otlp_span_tag}
 * additional table, the way the segment and Zipkin DAOs search their tag tables; the spans of the selected traces
 * are then fetched and grouped by trace in Java.
 */
@RequiredArgsConstructor
public class JDBCOTLPTraceQueryDAO implements IOTLPTraceQueryDAO {
    private static final int NAME_QUERY_MAX_SIZE = Integer.MAX_VALUE;

    /**
     * The alias of the aggregate a search ranks traces by, {@code min(start_time)} or {@code max(duration)}.
     */
    private static final String RANK_COLUMN = "rank_value";

    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<String> getServiceNames(@Nullable final Duration duration) {
        final List<String> tables = tableHelper.getTablesWithinTTL(OTLPServiceTraffic.INDEX_NAME);
        final Set<String> services = new LinkedHashSet<>();
        for (final String table : tables) {
            final String sql = "select " + OTLPServiceTraffic.SERVICE_NAME + " from " + table
                + " where " + JDBCTableInstaller.TABLE_COLUMN + " = ? limit " + NAME_QUERY_MAX_SIZE;
            jdbcClient.executeQuery(sql, resultSet -> {
                while (resultSet.next()) {
                    services.add(resultSet.getString(OTLPServiceTraffic.SERVICE_NAME));
                }
                return null;
            }, OTLPServiceTraffic.INDEX_NAME);
        }
        return new ArrayList<>(services);
    }

    @Override
    @SneakyThrows
    public List<String> getSpanNames(final String serviceName, @Nullable final Duration duration) {
        final List<String> tables = tableHelper.getTablesWithinTTL(OTLPServiceSpanTraffic.INDEX_NAME);
        final Set<String> spanNames = new LinkedHashSet<>();
        for (final String table : tables) {
            final String sql = "select " + OTLPServiceSpanTraffic.SPAN_NAME + " from " + table
                + " where " + JDBCTableInstaller.TABLE_COLUMN + " = ? and " + OTLPServiceSpanTraffic.SERVICE_NAME
                + " = ? limit " + NAME_QUERY_MAX_SIZE;
            jdbcClient.executeQuery(sql, resultSet -> {
                while (resultSet.next()) {
                    spanNames.add(resultSet.getString(OTLPServiceSpanTraffic.SPAN_NAME));
                }
                return null;
            }, OTLPServiceSpanTraffic.INDEX_NAME, serviceName);
        }
        return new ArrayList<>(spanNames);
    }

    @Override
    @SneakyThrows
    public List<String> getPeerServiceNames(final String serviceName, @Nullable final Duration duration) {
        final List<String> tables = tableHelper.getTablesWithinTTL(OTLPServiceRelationTraffic.INDEX_NAME);
        final Set<String> peerServices = new LinkedHashSet<>();
        for (final String table : tables) {
            final String sql = "select " + OTLPServiceRelationTraffic.PEER_SERVICE + " from " + table
                + " where " + JDBCTableInstaller.TABLE_COLUMN + " = ? and " + OTLPServiceRelationTraffic.SERVICE_NAME
                + " = ? limit " + NAME_QUERY_MAX_SIZE;
            jdbcClient.executeQuery(sql, resultSet -> {
                while (resultSet.next()) {
                    peerServices.add(resultSet.getString(OTLPServiceRelationTraffic.PEER_SERVICE));
                }
                return null;
            }, OTLPServiceRelationTraffic.INDEX_NAME, serviceName);
        }
        return new ArrayList<>(peerServices);
    }

    @Override
    @SneakyThrows
    public List<SpanWrapper> queryTraceById(final String traceId, @Nullable final Duration duration) {
        final List<String> tables = tablesFor(duration);
        final boolean bounded = duration != null && duration.getStartTimestamp() > 0 && duration.getEndTimestamp() > 0;
        final List<SpanWrapper> trace = new ArrayList<>();
        for (final String table : tables) {
            final StringBuilder sql = new StringBuilder("select * from ").append(table)
                .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? and ")
                .append(OTLPSpanRecord.TRACE_ID).append(" = ?");
            final List<Object> parameters = new ArrayList<>(List.of(OTLPSpanRecord.INDEX_NAME, traceId));
            if (bounded) {
                sql.append(" and ").append(OTLPSpanRecord.START_TIME).append(" >= ?")
                   .append(" and ").append(OTLPSpanRecord.START_TIME).append(" <= ?");
                parameters.add(duration.getStartTimestamp());
                parameters.add(duration.getEndTimestamp());
            }
            sql.append(" order by ").append(OTLPSpanRecord.START_TIME).append(" desc");
            jdbcClient.executeQuery(sql.toString(), resultSet -> {
                while (resultSet.next()) {
                    trace.add(buildSpanWrapper(resultSet));
                }
                return null;
            }, parameters.toArray(new Object[0]));
        }
        return trace;
    }

    /**
     * The day tables a time range covers, every table within the TTL without one.
     */
    private List<String> tablesFor(@Nullable final Duration duration) {
        return duration == null
            ? tableHelper.getTablesWithinTTL(OTLPSpanRecord.INDEX_NAME)
            : tableHelper.getTablesForRead(
                OTLPSpanRecord.INDEX_NAME, duration.getStartTimeBucket(), duration.getEndTimeBucket());
    }

    @Override
    @SneakyThrows
    public List<List<SpanWrapper>> queryTraces(final OTLPTraceQueryCondition condition) {
        final Duration duration = condition.getQueryDuration();
        final List<String> tables = tablesFor(duration);
        final boolean byDuration = condition.getQueryOrder() == QueryOrder.BY_DURATION;
        final String orderColumn = byDuration
            ? "max(" + OTLPSpanRecord.DURATION + ")" : "min(" + OTLPSpanRecord.START_TIME + ")";
        // Every day table answers its own top `limit`; a trace that crosses midnight is in two of them, so the
        // candidates are merged on the rank value and ranked once more before the limit applies to the whole range.
        final Map<String, Long> rankByTraceId = new HashMap<>();
        for (final String table : tables) {
            final StringBuilder sql = new StringBuilder();
            final List<Object> parameters = new ArrayList<>();
            final List<Tag> tags = condition.getTags() == null ? Collections.emptyList() : condition.getTags();
            sql.append("select ").append(table).append(".").append(OTLPSpanRecord.TRACE_ID).append(", ")
               .append(orderColumn).append(" as ").append(RANK_COLUMN).append(" from ").append(table);
            final long timeBucket = TableHelper.getTimeBucket(table);
            final String tagTable = TableHelper.getTable(OTLPSpanRecord.ADDITIONAL_TAG_TABLE, timeBucket);
            for (int i = 0; i < tags.size(); i++) {
                sql.append(" inner join ").append(tagTable).append(" ").append(tagTable).append(i);
                sql.append(" on ").append(table).append(".").append(JDBCTableInstaller.ID_COLUMN).append(" = ")
                   .append(tagTable).append(i).append(".").append(JDBCTableInstaller.ID_COLUMN);
            }
            sql.append(" where ").append(table).append(".").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            parameters.add(OTLPSpanRecord.INDEX_NAME);
            if (duration != null && duration.getStartTimestamp() > 0 && duration.getEndTimestamp() > 0) {
                sql.append(" and ").append(OTLPSpanRecord.START_TIME).append(" >= ?");
                parameters.add(duration.getStartTimestamp());
                sql.append(" and ").append(OTLPSpanRecord.START_TIME).append(" <= ?");
                parameters.add(duration.getEndTimestamp());
            }
            appendEquals(sql, parameters, table, OTLPSpanRecord.TRACE_ID, condition.getTraceId());
            appendEquals(sql, parameters, table, OTLPSpanRecord.SERVICE_NAME, condition.getServiceName());
            appendEquals(sql, parameters, table, OTLPSpanRecord.SERVICE_INSTANCE, condition.getServiceInstance());
            appendEquals(sql, parameters, table, OTLPSpanRecord.SCOPE_NAME, condition.getScopeName());
            appendEquals(sql, parameters, table, OTLPSpanRecord.NAME, condition.getSpanName());
            appendEquals(sql, parameters, table, OTLPSpanRecord.PEER_SERVICE, condition.getPeerService());
            if (condition.getKind() != null) {
                sql.append(" and ").append(OTLPSpanRecord.KIND).append(" = ?");
                parameters.add(condition.getKind());
            }
            if (condition.getStatusCode() != null) {
                sql.append(" and ").append(OTLPSpanRecord.STATUS_CODE).append(" = ?");
                parameters.add(condition.getStatusCode());
            }
            if (condition.getMinDurationNanos() != null) {
                sql.append(" and ").append(OTLPSpanRecord.DURATION).append(" >= ?");
                parameters.add(condition.getMinDurationNanos());
            }
            if (condition.getMaxDurationNanos() != null) {
                sql.append(" and ").append(OTLPSpanRecord.DURATION).append(" <= ?");
                parameters.add(condition.getMaxDurationNanos());
            }
            for (int i = 0; i < tags.size(); i++) {
                // one form for a scoped key, either of the two for an unscoped one
                final List<String> forms = OTLPSpanRecord.indexedTags(tags.get(i).getKey(), tags.get(i).getValue());
                sql.append(" and ").append(tagTable).append(i).append(".").append(OTLPSpanRecord.TAGS);
                if (forms.size() == 1) {
                    sql.append(" = ?");
                } else {
                    sql.append(" in (").append(String.join(",", Collections.nCopies(forms.size(), "?"))).append(")");
                }
                parameters.addAll(forms);
            }
            sql.append(" group by ").append(table).append(".").append(OTLPSpanRecord.TRACE_ID);
            sql.append(" order by ").append(orderColumn).append(" desc");
            sql.append(" limit ").append(condition.getLimit());
            jdbcClient.executeQuery(sql.toString(), resultSet -> {
                while (resultSet.next()) {
                    rankByTraceId.merge(
                        resultSet.getString(OTLPSpanRecord.TRACE_ID), resultSet.getLong(RANK_COLUMN),
                        byDuration ? Math::max : Math::min);
                }
                return null;
            }, parameters.toArray(new Object[0]));
        }
        final Set<String> traceIds = rankByTraceId.entrySet().stream()
                                                  .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                                  .limit(condition.getLimit())
                                                  .map(Map.Entry::getKey)
                                                  .collect(Collectors.toCollection(LinkedHashSet::new));
        return fetchTraces(traceIds);
    }

    private static void appendEquals(final StringBuilder sql,
                                     final List<Object> parameters,
                                     final String table,
                                     final String column,
                                     final String value) {
        if (StringUtil.isEmpty(value)) {
            return;
        }
        sql.append(" and ").append(table).append(".").append(column).append(" = ?");
        parameters.add(value);
    }

    @SneakyThrows
    private List<List<SpanWrapper>> fetchTraces(final Set<String> traceIds) {
        if (CollectionUtils.isEmpty(traceIds)) {
            return new ArrayList<>();
        }
        final Map<String, List<SpanWrapper>> groupedByTraceId = new LinkedHashMap<>();
        for (final String traceId : traceIds) {
            groupedByTraceId.put(traceId, new ArrayList<>());
        }
        final List<String> tables = tableHelper.getTablesWithinTTL(OTLPSpanRecord.INDEX_NAME);
        for (final String table : tables) {
            final StringBuilder sql = new StringBuilder();
            final List<Object> parameters = new ArrayList<>(traceIds.size() + 1);
            sql.append("select * from ").append(table);
            sql.append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            parameters.add(OTLPSpanRecord.INDEX_NAME);
            sql.append(" and ").append(OTLPSpanRecord.TRACE_ID)
               .append(" in (").append(String.join(",", Collections.nCopies(traceIds.size(), "?"))).append(")");
            parameters.addAll(traceIds);
            sql.append(" order by ").append(OTLPSpanRecord.START_TIME).append(" desc");
            jdbcClient.executeQuery(sql.toString(), resultSet -> {
                while (resultSet.next()) {
                    final String traceId = resultSet.getString(OTLPSpanRecord.TRACE_ID);
                    groupedByTraceId.computeIfAbsent(traceId, k -> new ArrayList<>()).add(buildSpanWrapper(resultSet));
                }
                return null;
            }, parameters.toArray(new Object[0]));
        }
        final List<List<SpanWrapper>> traces = new ArrayList<>(groupedByTraceId.size());
        for (final List<SpanWrapper> trace : groupedByTraceId.values()) {
            if (!trace.isEmpty()) {
                traces.add(trace);
            }
        }
        return traces;
    }

    /**
     * The JDBC storages keep {@code data_binary} as base64 text, the way the segment DAO reads it.
     */
    private static SpanWrapper buildSpanWrapper(final ResultSet resultSet) throws SQLException {
        final OTLPSpanRecord record = new OTLPSpanRecord();
        record.setTraceId(resultSet.getString(OTLPSpanRecord.TRACE_ID));
        record.setSpanId(resultSet.getString(OTLPSpanRecord.SPAN_ID));
        final String dataBinaryBase64 = resultSet.getString(OTLPSpanRecord.DATA_BINARY);
        if (!Strings.isNullOrEmpty(dataBinaryBase64)) {
            record.setDataBinary(Base64.getDecoder().decode(dataBinaryBase64));
        } else {
            record.setDataBinary(new byte[0]);
        }
        return record.getSpanWrapper();
    }
}
