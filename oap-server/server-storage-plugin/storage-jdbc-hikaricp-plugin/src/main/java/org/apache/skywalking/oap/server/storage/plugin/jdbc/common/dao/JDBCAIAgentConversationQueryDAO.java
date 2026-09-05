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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.query.IAIAgentConversationQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import static java.util.stream.Collectors.joining;

/**
 * Every query condition is a <code>WHERE</code> clause on a plain column. Both models are super datasets, so each
 * has its own table per day; a read spans the tables of the range and merges in memory.
 */
@RequiredArgsConstructor
public class JDBCAIAgentConversationQueryDAO implements IAIAgentConversationQueryDAO {
    private static final List<String> ROUND_COLUMNS = List.of(
        AIAgentSessionFlowRecord.SERVICE_ID,
        AIAgentSessionFlowRecord.SERVICE_INSTANCE_ID,
        AIAgentSessionFlowRecord.CONVERSATION,
        AIAgentSessionFlowRecord.ROUND,
        AIAgentSessionFlowRecord.SESSION_FROM_TIME,
        AIAgentSessionFlowRecord.TITLE,
        AIAgentSessionFlowRecord.TALKS,
        AIAgentSessionFlowRecord.STEPS,
        AIAgentSessionFlowRecord.STREAMS,
        AIAgentSessionFlowRecord.SEGMENTS,
        AIAgentSessionFlowRecord.UNRESOLVED,
        AIAgentSessionFlowRecord.DIGEST,
        AIAgentSessionFlowRecord.TIMESTAMP
    );
    private static final List<String> FILE_COLUMNS = List.of(
        AIAgentSessionDataRecord.SERVICE_ID,
        AIAgentSessionDataRecord.SERVICE_INSTANCE_ID,
        AIAgentSessionDataRecord.SESSION,
        AIAgentSessionDataRecord.SEQ,
        AIAgentSessionDataRecord.DIGEST,
        AIAgentSessionDataRecord.TIMESTAMP,
        AIAgentSessionDataRecord.BODY
    );

    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<AIAgentSessionFlowRecord> queryRounds(final String serviceId,
                                                      @Nullable final String serviceInstanceId,
                                                      @Nullable final String conversation,
                                                      @Nullable final Duration duration,
                                                      final int limit,
                                                      final boolean includeBody) {
        final List<String> tables = duration == null
            ? tableHelper.getTablesWithinTTL(AIAgentSessionFlowRecord.INDEX_NAME)
            : tableHelper.getTablesForRead(
                AIAgentSessionFlowRecord.INDEX_NAME, duration.getStartTimeBucket(), duration.getEndTimeBucket());
        final List<String> columns = new ArrayList<>(ROUND_COLUMNS);
        if (includeBody) {
            columns.add(AIAgentSessionFlowRecord.BODY);
        }
        final List<AIAgentSessionFlowRecord> rounds = new ArrayList<>();
        for (final String table : tables) {
            final StringBuilder sql = new StringBuilder("select ");
            final List<Object> parameters = new ArrayList<>();
            sql.append(select(AIAgentSessionFlowRecord.INDEX_NAME, columns))
               .append(" from ").append(table)
               .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            parameters.add(AIAgentSessionFlowRecord.INDEX_NAME);
            if (duration != null) {
                sql.append(" and ").append(column(AIAgentSessionFlowRecord.INDEX_NAME, Record.TIME_BUCKET))
                   .append(" >= ?");
                parameters.add(duration.getStartTimeBucketInSec());
                sql.append(" and ").append(column(AIAgentSessionFlowRecord.INDEX_NAME, Record.TIME_BUCKET))
                   .append(" <= ?");
                parameters.add(duration.getEndTimeBucketInSec());
            }
            sql.append(" and ").append(column(AIAgentSessionFlowRecord.INDEX_NAME, AIAgentSessionFlowRecord.SERVICE_ID))
               .append(" = ?");
            parameters.add(serviceId);
            if (StringUtil.isNotEmpty(serviceInstanceId)) {
                sql.append(" and ")
                   .append(column(AIAgentSessionFlowRecord.INDEX_NAME, AIAgentSessionFlowRecord.SERVICE_INSTANCE_ID))
                   .append(" = ?");
                parameters.add(serviceInstanceId);
            }
            if (StringUtil.isNotEmpty(conversation)) {
                sql.append(" and ")
                   .append(column(AIAgentSessionFlowRecord.INDEX_NAME, AIAgentSessionFlowRecord.CONVERSATION))
                   .append(" = ?");
                parameters.add(conversation);
            }
            sql.append(" order by ").append(column(AIAgentSessionFlowRecord.INDEX_NAME, AIAgentSessionFlowRecord.TIMESTAMP))
               .append(" desc limit ").append(limit);
            rounds.addAll(jdbcClient.executeQuery(sql.toString(), rs -> parseRounds(rs, includeBody), parameters.toArray()));
        }
        rounds.sort(Comparator.comparingLong(AIAgentSessionFlowRecord::getTimestamp).reversed());
        return rounds.size() > limit ? new ArrayList<>(rounds.subList(0, limit)) : rounds;
    }

    @Override
    @SneakyThrows
    public List<AIAgentSessionFlowRecord> queryRoundsByNumber(final String serviceId,
                                                              @Nullable final String serviceInstanceId,
                                                              final String conversation,
                                                              final long fromRound,
                                                              final long throughRound) {
        final List<String> columns = new ArrayList<>(ROUND_COLUMNS);
        columns.add(AIAgentSessionFlowRecord.BODY);
        final List<AIAgentSessionFlowRecord> rounds = new ArrayList<>();
        // every table within the TTL: the rounds of a long conversation span days
        for (final String table : tableHelper.getTablesWithinTTL(AIAgentSessionFlowRecord.INDEX_NAME)) {
            final StringBuilder sql = new StringBuilder("select ");
            final List<Object> parameters = new ArrayList<>();
            sql.append(select(AIAgentSessionFlowRecord.INDEX_NAME, columns))
               .append(" from ").append(table)
               .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            parameters.add(AIAgentSessionFlowRecord.INDEX_NAME);
            sql.append(" and ").append(column(AIAgentSessionFlowRecord.INDEX_NAME, AIAgentSessionFlowRecord.SERVICE_ID))
               .append(" = ?");
            parameters.add(serviceId);
            if (StringUtil.isNotEmpty(serviceInstanceId)) {
                sql.append(" and ")
                   .append(column(AIAgentSessionFlowRecord.INDEX_NAME, AIAgentSessionFlowRecord.SERVICE_INSTANCE_ID))
                   .append(" = ?");
                parameters.add(serviceInstanceId);
            }
            sql.append(" and ").append(column(AIAgentSessionFlowRecord.INDEX_NAME, AIAgentSessionFlowRecord.CONVERSATION))
               .append(" = ?");
            parameters.add(conversation);
            sql.append(" and ").append(column(AIAgentSessionFlowRecord.INDEX_NAME, AIAgentSessionFlowRecord.ROUND))
               .append(" >= ? and ").append(column(AIAgentSessionFlowRecord.INDEX_NAME, AIAgentSessionFlowRecord.ROUND))
               .append(" <= ?");
            parameters.add(fromRound);
            parameters.add(throughRound);
            sql.append(" order by ").append(column(AIAgentSessionFlowRecord.INDEX_NAME, AIAgentSessionFlowRecord.ROUND))
               .append(" asc");
            rounds.addAll(jdbcClient.executeQuery(sql.toString(), rs -> parseRounds(rs, true), parameters.toArray()));
        }
        rounds.sort(Comparator.comparingLong(AIAgentSessionFlowRecord::getRound));
        return rounds;
    }

    @Override
    @SneakyThrows
    public List<AIAgentSessionDataRecord> queryFiles(final String serviceId,
                                                     @Nullable final String serviceInstanceId,
                                                     final String session,
                                                     final long fromTimestamp,
                                                     final long toTimestamp,
                                                     final long fromSeq,
                                                     final long throughSeq) {
        final List<String> tables = tableHelper.getTablesForRead(
            AIAgentSessionDataRecord.INDEX_NAME,
            TimeBucket.getTimeBucket(fromTimestamp, DownSampling.Day),
            TimeBucket.getTimeBucket(toTimestamp, DownSampling.Day)
        );
        final List<AIAgentSessionDataRecord> files = new ArrayList<>();
        for (final String table : tables) {
            final StringBuilder sql = new StringBuilder("select ");
            final List<Object> parameters = new ArrayList<>();
            sql.append(select(AIAgentSessionDataRecord.INDEX_NAME, FILE_COLUMNS))
               .append(" from ").append(table)
               .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            parameters.add(AIAgentSessionDataRecord.INDEX_NAME);
            sql.append(" and ").append(column(AIAgentSessionDataRecord.INDEX_NAME, AIAgentSessionDataRecord.SERVICE_ID))
               .append(" = ?");
            parameters.add(serviceId);
            if (StringUtil.isNotEmpty(serviceInstanceId)) {
                sql.append(" and ")
                   .append(column(AIAgentSessionDataRecord.INDEX_NAME, AIAgentSessionDataRecord.SERVICE_INSTANCE_ID))
                   .append(" = ?");
                parameters.add(serviceInstanceId);
            }
            sql.append(" and ").append(column(AIAgentSessionDataRecord.INDEX_NAME, AIAgentSessionDataRecord.SESSION))
               .append(" = ?");
            parameters.add(session);
            sql.append(" and ").append(column(AIAgentSessionDataRecord.INDEX_NAME, AIAgentSessionDataRecord.SEQ))
               .append(" >= ? and ").append(column(AIAgentSessionDataRecord.INDEX_NAME, AIAgentSessionDataRecord.SEQ))
               .append(" <= ?");
            parameters.add(fromSeq);
            parameters.add(throughSeq);
            sql.append(" and ").append(column(AIAgentSessionDataRecord.INDEX_NAME, AIAgentSessionDataRecord.TIMESTAMP))
               .append(" >= ? and ").append(column(AIAgentSessionDataRecord.INDEX_NAME, AIAgentSessionDataRecord.TIMESTAMP))
               .append(" <= ?");
            parameters.add(fromTimestamp);
            parameters.add(toTimestamp);
            sql.append(" order by ").append(column(AIAgentSessionDataRecord.INDEX_NAME, AIAgentSessionDataRecord.SEQ))
               .append(" asc");
            files.addAll(jdbcClient.executeQuery(sql.toString(), this::parseFiles, parameters.toArray()));
        }
        files.sort(Comparator.comparingLong(AIAgentSessionDataRecord::getSeq));
        return files;
    }

    private List<AIAgentSessionFlowRecord> parseRounds(final ResultSet resultSet, final boolean includeBody)
        throws SQLException {
        final List<AIAgentSessionFlowRecord> rounds = new ArrayList<>();
        while (resultSet.next()) {
            final AIAgentSessionFlowRecord record = new AIAgentSessionFlowRecord();
            record.setServiceId(resultSet.getString(AIAgentSessionFlowRecord.SERVICE_ID));
            record.setServiceInstanceId(resultSet.getString(AIAgentSessionFlowRecord.SERVICE_INSTANCE_ID));
            record.setConversation(resultSet.getString(AIAgentSessionFlowRecord.CONVERSATION));
            record.setRound(resultSet.getLong(AIAgentSessionFlowRecord.ROUND));
            record.setSessionFromTime(resultSet.getLong(AIAgentSessionFlowRecord.SESSION_FROM_TIME));
            record.setTitle(resultSet.getString(AIAgentSessionFlowRecord.TITLE));
            record.setTalks(resultSet.getLong(AIAgentSessionFlowRecord.TALKS));
            record.setSteps(resultSet.getLong(AIAgentSessionFlowRecord.STEPS));
            record.setStreams(resultSet.getLong(AIAgentSessionFlowRecord.STREAMS));
            record.setSegments(resultSet.getLong(AIAgentSessionFlowRecord.SEGMENTS));
            record.setUnresolved(resultSet.getLong(AIAgentSessionFlowRecord.UNRESOLVED));
            record.setDigest(resultSet.getString(AIAgentSessionFlowRecord.DIGEST));
            final long timestamp = resultSet.getLong(AIAgentSessionFlowRecord.TIMESTAMP);
            record.setTimestamp(timestamp);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
            if (includeBody) {
                record.setBody(bytesOf(resultSet.getString(AIAgentSessionFlowRecord.BODY)));
            }
            rounds.add(record);
        }
        return rounds;
    }

    private List<AIAgentSessionDataRecord> parseFiles(final ResultSet resultSet) throws SQLException {
        final List<AIAgentSessionDataRecord> files = new ArrayList<>();
        while (resultSet.next()) {
            final AIAgentSessionDataRecord record = new AIAgentSessionDataRecord();
            record.setServiceId(resultSet.getString(AIAgentSessionDataRecord.SERVICE_ID));
            record.setServiceInstanceId(resultSet.getString(AIAgentSessionDataRecord.SERVICE_INSTANCE_ID));
            record.setSession(resultSet.getString(AIAgentSessionDataRecord.SESSION));
            record.setSeq(resultSet.getLong(AIAgentSessionDataRecord.SEQ));
            record.setDigest(resultSet.getString(AIAgentSessionDataRecord.DIGEST));
            final long timestamp = resultSet.getLong(AIAgentSessionDataRecord.TIMESTAMP);
            record.setTimestamp(timestamp);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
            record.setBody(bytesOf(resultSet.getString(AIAgentSessionDataRecord.BODY)));
            files.add(record);
        }
        return files;
    }

    /**
     * A <code>byte[]</code> column is written as base64 text, the way the segment body is.
     */
    private static byte[] bytesOf(final String value) {
        return StringUtil.isEmpty(value) ? null : Base64.getDecoder().decode(value);
    }

    private static String select(final String model, final List<String> columns) {
        return columns.stream().map(c -> column(model, c)).collect(joining(", "));
    }

    private static String column(final String model, final String logicalColumn) {
        return TableMetaInfo.get(model)
                            .getColumns()
                            .stream()
                            .map(ModelColumn::getColumnName)
                            .filter(it -> logicalColumn.equals(it.getName()))
                            .findFirst()
                            .map(ColumnName::getStorageName)
                            .orElse(logicalColumn);
    }
}
