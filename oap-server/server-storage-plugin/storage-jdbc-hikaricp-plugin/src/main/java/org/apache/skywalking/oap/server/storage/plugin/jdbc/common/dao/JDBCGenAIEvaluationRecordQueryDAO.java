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
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.analysis.manual.genai.GenAIEvaluationRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.GenAIEvaluationRecords;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.query.IGenAIEvaluationRecordQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;

import static java.util.Comparator.comparing;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class JDBCGenAIEvaluationRecordQueryDAO implements IGenAIEvaluationRecordQueryDAO {
    private static final Set<String> QUERYABLE_TAG_KEYS = Set.of(
        GenAIEvaluationRecord.TRACE_ID,
        GenAIEvaluationRecord.SERVICE_ID,
        GenAIEvaluationRecord.SERVICE_INSTANCE_ID,
        GenAIEvaluationRecord.SEGMENT_ID,
        GenAIEvaluationRecord.SPAN_ID,
        GenAIEvaluationRecord.SPAN_TYPE,
        GenAIEvaluationRecord.TASK_NAME,
        GenAIEvaluationRecord.VALUE_TYPE,
        GenAIEvaluationRecord.VALUE,
        GenAIEvaluationRecord.EVALUATION_LEVEL,
        GenAIEvaluationRecord.REASON,
        GenAIEvaluationRecord.JUDGE_MODEL
    );

    private static final List<String> SELECTED_COLUMNS = List.of(
        GenAIEvaluationRecord.TRACE_ID,
        GenAIEvaluationRecord.SERVICE_ID,
        GenAIEvaluationRecord.SERVICE_INSTANCE_ID,
        GenAIEvaluationRecord.SEGMENT_ID,
        GenAIEvaluationRecord.SPAN_ID,
        GenAIEvaluationRecord.SPAN_TYPE,
        GenAIEvaluationRecord.TASK_NAME,
        GenAIEvaluationRecord.VALUE_TYPE,
        GenAIEvaluationRecord.VALUE,
        GenAIEvaluationRecord.EVALUATION_LEVEL,
        GenAIEvaluationRecord.REASON,
        GenAIEvaluationRecord.JUDGE_MODEL,
        GenAIEvaluationRecord.EVALUATION_TIME
    );

    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public GenAIEvaluationRecords queryGenAIEvaluationRecord(final String serviceId,
                                                             final String serviceInstanceId,
                                                             final TraceScopeCondition relatedTrace,
                                                             final Order queryOrder,
                                                             final int from,
                                                             final int limit,
                                                             final Duration duration,
                                                             final List<Tag> tags) {
        if (CollectionUtils.isNotEmpty(tags)) {
            for (final Tag tag : tags) {
                if (StringUtil.isNotEmpty(tag.getKey())
                    && StringUtil.isNotEmpty(tag.getValue())
                    && !QUERYABLE_TAG_KEYS.contains(tag.getKey())) {
                    return new GenAIEvaluationRecords();
                }
            }
        }

        final List<String> tables;
        if (nonNull(duration)) {
            tables = tableHelper.getTablesForRead(
                GenAIEvaluationRecord.INDEX_NAME,
                duration.getStartTimeBucket(),
                duration.getEndTimeBucket()
            );
        } else {
            tables = tableHelper.getTablesWithinTTL(GenAIEvaluationRecord.INDEX_NAME);
        }

        final var records = new ArrayList<GenAIEvaluationRecord>();
        for (final var table : tables) {
            final var sqlAndParameters = buildSQL(
                serviceId, serviceInstanceId, relatedTrace, queryOrder, from, limit, duration, tags, table);
            records.addAll(
                jdbcClient.executeQuery(
                    sqlAndParameters.sql(),
                    this::parseResults,
                    sqlAndParameters.parameters()
                )
            );
        }

        final var comparator = Order.ASC.equals(queryOrder) ?
            comparing(GenAIEvaluationRecord::getEvaluationTime) :
            comparing(GenAIEvaluationRecord::getEvaluationTime).reversed();
        return new GenAIEvaluationRecords(
            records.stream().sorted(comparator).skip(from).limit(limit).collect(toList())
        );
    }

    protected ArrayList<GenAIEvaluationRecord> parseResults(final ResultSet resultSet) throws SQLException {
        final var records = new ArrayList<GenAIEvaluationRecord>();
        while (resultSet.next()) {
            final GenAIEvaluationRecord record = new GenAIEvaluationRecord();
            record.setTraceId(resultSet.getString(GenAIEvaluationRecord.TRACE_ID));
            record.setServiceId(resultSet.getString(GenAIEvaluationRecord.SERVICE_ID));
            record.setServiceInstanceId(resultSet.getString(GenAIEvaluationRecord.SERVICE_INSTANCE_ID));
            record.setSegmentId(resultSet.getString(GenAIEvaluationRecord.SEGMENT_ID));
            record.setSpanId(resultSet.getString(GenAIEvaluationRecord.SPAN_ID));
            record.setSpanType(resultSet.getString(GenAIEvaluationRecord.SPAN_TYPE));
            record.setTaskName(resultSet.getString(GenAIEvaluationRecord.TASK_NAME));
            record.setValueType(resultSet.getString(GenAIEvaluationRecord.VALUE_TYPE));
            record.setValue(resultSet.getString(GenAIEvaluationRecord.VALUE));
            record.setEvaluationLevel(resultSet.getString(GenAIEvaluationRecord.EVALUATION_LEVEL));
            record.setReason(resultSet.getString(GenAIEvaluationRecord.REASON));
            record.setJudgeModel(resultSet.getString(GenAIEvaluationRecord.JUDGE_MODEL));
            record.setEvaluationTime(resultSet.getLong(GenAIEvaluationRecord.EVALUATION_TIME));
            records.add(record);
        }
        return records;
    }

    protected SQLAndParameters buildSQL(final String serviceId,
                                        final String serviceInstanceId,
                                        final TraceScopeCondition relatedTrace,
                                        final Order queryOrder,
                                        final int from,
                                        final int limit,
                                        final Duration duration,
                                        final List<Tag> tags,
                                        final String table) {
        long startSecondTB = 0;
        long endSecondTB = 0;
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
        }

        final StringBuilder sql = new StringBuilder("select ");
        final List<Object> parameters = new ArrayList<>(10);
        sql.append(selectColumns())
           .append(" from ")
           .append(table)
           .append(" where ")
           .append(JDBCTableInstaller.TABLE_COLUMN)
           .append(" = ?");
        parameters.add(GenAIEvaluationRecord.INDEX_NAME);

        if (startSecondTB != 0 && endSecondTB != 0) {
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.TIME_BUCKET)).append(" >= ?");
            parameters.add(startSecondTB);
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.TIME_BUCKET)).append(" <= ?");
            parameters.add(endSecondTB);
        }
        if (StringUtil.isNotEmpty(serviceId)) {
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.SERVICE_ID)).append(" = ?");
            parameters.add(serviceId);
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.SERVICE_INSTANCE_ID)).append(" = ?");
            parameters.add(serviceInstanceId);
        }
        if (nonNull(relatedTrace)) {
            if (StringUtil.isNotEmpty(relatedTrace.getTraceId())) {
                sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.TRACE_ID)).append(" = ?");
                parameters.add(relatedTrace.getTraceId());
            }
            if (StringUtil.isNotEmpty(relatedTrace.getSegmentId())) {
                sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.SEGMENT_ID)).append(" = ?");
                parameters.add(relatedTrace.getSegmentId());
            }
            if (nonNull(relatedTrace.getSpanId())) {
                sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.SPAN_ID)).append(" = ?");
                parameters.add(String.valueOf(relatedTrace.getSpanId()));
            }
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            for (final Tag tag : tags) {
                if (StringUtil.isNotEmpty(tag.getKey())
                    && StringUtil.isNotEmpty(tag.getValue())
                    && QUERYABLE_TAG_KEYS.contains(tag.getKey())) {
                    sql.append(" and ").append(storageColumn(tag.getKey())).append(" = ?");
                    parameters.add(tag.getValue());
                }
            }
        }

        sql.append(" order by ")
           .append(storageColumn(GenAIEvaluationRecord.EVALUATION_TIME))
           .append(" ")
           .append(Order.DES.equals(queryOrder) ? "desc" : "asc");
        sql.append(" limit ").append(from + limit);

        return new SQLAndParameters(sql.toString(), parameters);
    }

    private String selectColumns() {
        return SELECTED_COLUMNS.stream()
                                 .map(this::selectColumn)
                                 .collect(java.util.stream.Collectors.joining(", "));
    }

    private String selectColumn(final String logicalColumn) {
        final String storageColumn = storageColumn(logicalColumn);
        if (storageColumn.equals(logicalColumn)) {
            return storageColumn;
        }
        return storageColumn + " as " + logicalColumn;
    }

    private String storageColumn(final String logicalColumn) {
        return TableMetaInfo.get(GenAIEvaluationRecord.INDEX_NAME)
                            .getColumns()
                            .stream()
                            .map(ModelColumn::getColumnName)
                            .filter(it -> logicalColumn.equals(it.getName()))
                            .findFirst()
                            .map(ColumnName::getStorageName)
                            .orElse(logicalColumn);
    }
}
