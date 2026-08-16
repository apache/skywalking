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
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.analysis.manual.genai.GenAIEvaluationRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationRecordSortBy;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationValueType;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.GenAIEvaluationRecords;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.query.IGenAIEvaluationRecordQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
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
    private static final List<String> SELECTED_COLUMNS = List.of(
        GenAIEvaluationRecord.UNIQUE_ID,
        GenAIEvaluationRecord.TRACE_ID,
        GenAIEvaluationRecord.SERVICE_ID,
        GenAIEvaluationRecord.PROVIDER_ID,
        GenAIEvaluationRecord.MODEL_ID,
        GenAIEvaluationRecord.OPERATION_NAME,
        GenAIEvaluationRecord.EVAL_NUMBER_VALUE,
        GenAIEvaluationRecord.REF_TYPE,
        GenAIEvaluationRecord.SEGMENT_ID,
        GenAIEvaluationRecord.SPAN_INDEX,
        GenAIEvaluationRecord.SPAN_ID,
        GenAIEvaluationRecord.TASK_NAME,
        GenAIEvaluationRecord.VALUE_TYPE,
        GenAIEvaluationRecord.EVAL_STRING_VALUE,
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
                                                             final String providerId,
                                                             final String modelId,
                                                             final GenAIEvaluationValueType valueType,
                                                             final Long minScore, final Long maxScore, final Boolean booleanValue,
                                                             final GenAIEvaluationRecordSortBy sortBy,
                                                             final String taskName, final String evaluationLevel, final String judgeModel,
                                                             final TraceScopeCondition relatedTrace,
                                                             final Order queryOrder,
                                                             final int from,
                                                             final int limit,
                                                             final Duration duration) {
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
                serviceId, providerId, modelId, valueType, minScore, maxScore, booleanValue, sortBy, taskName, evaluationLevel, judgeModel,
                relatedTrace, queryOrder, from, limit, duration, table);
            records.addAll(
                jdbcClient.executeQuery(
                    sqlAndParameters.sql(),
                    this::parseResults,
                    sqlAndParameters.parameters()
                )
            );
        }

        Comparator<GenAIEvaluationRecord> comparator = GenAIEvaluationRecordSortBy.SCORE_VALUE.equals(sortBy)
            ? comparing((GenAIEvaluationRecord record) -> record.getScoreValue() == null ? Double.NEGATIVE_INFINITY : record.getScoreValue())
            : comparing(GenAIEvaluationRecord::getEvaluationTime);
        if (Order.DES.equals(queryOrder)) {
            comparator = comparator.reversed();
        }
        return new GenAIEvaluationRecords(
            records.stream().sorted(comparator).skip(from).limit(limit).collect(toList())
        );
    }

    protected ArrayList<GenAIEvaluationRecord> parseResults(final ResultSet resultSet) throws SQLException {
        final var records = new ArrayList<GenAIEvaluationRecord>();
        while (resultSet.next()) {
            final GenAIEvaluationRecord record = new GenAIEvaluationRecord();
            record.setUniqueId(resultSet.getString(GenAIEvaluationRecord.UNIQUE_ID));
            record.setTraceId(resultSet.getString(GenAIEvaluationRecord.TRACE_ID));
            record.setServiceId(resultSet.getString(GenAIEvaluationRecord.SERVICE_ID));
            record.setProviderId(resultSet.getString(GenAIEvaluationRecord.PROVIDER_ID));
            record.setModelId(resultSet.getString(GenAIEvaluationRecord.MODEL_ID));
            record.setOperationName(resultSet.getString(GenAIEvaluationRecord.OPERATION_NAME));
            final long numberValue = resultSet.getLong(GenAIEvaluationRecord.EVAL_NUMBER_VALUE);
            record.setEvalNumberValue(resultSet.wasNull() ? null : numberValue);
            record.setRefType(resultSet.getString(GenAIEvaluationRecord.REF_TYPE));
            record.setSegmentId(resultSet.getString(GenAIEvaluationRecord.SEGMENT_ID));
            final int spanIndex = resultSet.getInt(GenAIEvaluationRecord.SPAN_INDEX);
            record.setSpanIndex(resultSet.wasNull() ? null : spanIndex);
            record.setSpanId(resultSet.getString(GenAIEvaluationRecord.SPAN_ID));
            record.setTaskName(resultSet.getString(GenAIEvaluationRecord.TASK_NAME));
            record.setValueType(resultSet.getString(GenAIEvaluationRecord.VALUE_TYPE));
            record.setEvalStringValue(resultSet.getString(GenAIEvaluationRecord.EVAL_STRING_VALUE));
            record.setEvaluationLevel(resultSet.getString(GenAIEvaluationRecord.EVALUATION_LEVEL));
            record.setReason(resultSet.getString(GenAIEvaluationRecord.REASON));
            record.setJudgeModel(resultSet.getString(GenAIEvaluationRecord.JUDGE_MODEL));
            record.setEvaluationTime(resultSet.getLong(GenAIEvaluationRecord.EVALUATION_TIME));
            records.add(record);
        }
        return records;
    }

    protected SQLAndParameters buildSQL(final String serviceId,
                                        final String providerId,
                                        final String modelId,
                                        final GenAIEvaluationValueType valueType,
                                        final Long minScore,
                                        final Long maxScore,
                                        final Boolean booleanValue,
                                        final GenAIEvaluationRecordSortBy sortBy,
                                        final String taskName,
                                        final String evaluationLevel,
                                        final String judgeModel,
                                        final TraceScopeCondition relatedTrace,
                                        final Order queryOrder,
                                        final int from,
                                        final int limit,
                                        final Duration duration,
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
        if (StringUtil.isNotEmpty(providerId)) {
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.PROVIDER_ID)).append(" = ?");
            parameters.add(providerId);
        }
        if (StringUtil.isNotEmpty(modelId)) {
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.MODEL_ID)).append(" = ?");
            parameters.add(modelId);
        }
        if (valueType != null) {
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.VALUE_TYPE)).append(" = ?");
            parameters.add(valueType.name());
        }
        if (booleanValue != null) {
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.EVAL_NUMBER_VALUE)).append(" = ?");
            parameters.add(booleanValue ? GenAIEvaluationRecord.SCORE_SCALE : 0);
        } else if (minScore != null) {
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.EVAL_NUMBER_VALUE)).append(" >= ?");
            parameters.add(minScore);
        }
        if (booleanValue == null && maxScore != null) {
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.EVAL_NUMBER_VALUE)).append(" <= ?");
            parameters.add(maxScore);
        }
        if (StringUtil.isNotEmpty(taskName)) {
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.TASK_NAME)).append(" = ?");
            parameters.add(taskName);
        }
        if (StringUtil.isNotEmpty(evaluationLevel)) {
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.EVALUATION_LEVEL)).append(" = ?");
            parameters.add(evaluationLevel);
        }
        if (StringUtil.isNotEmpty(judgeModel)) {
            sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.JUDGE_MODEL)).append(" = ?");
            parameters.add(judgeModel);
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
                sql.append(" and ").append(storageColumn(GenAIEvaluationRecord.SPAN_INDEX)).append(" = ?");
                parameters.add(relatedTrace.getSpanId());
            }
        }
        sql.append(" order by ")
           .append(storageColumn(GenAIEvaluationRecordSortBy.SCORE_VALUE.equals(sortBy)
               ? GenAIEvaluationRecord.EVAL_NUMBER_VALUE : GenAIEvaluationRecord.EVALUATION_TIME))
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
