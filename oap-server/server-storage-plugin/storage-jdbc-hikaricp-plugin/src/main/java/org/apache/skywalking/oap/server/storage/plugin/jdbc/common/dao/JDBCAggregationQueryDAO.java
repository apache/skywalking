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

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JDBCAggregationQueryDAO implements IAggregationQueryDAO {
    protected final JDBCClient jdbcClient;
    protected final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<SelectedRecord> sortMetrics(final TopNCondition metrics,
                                            final String valueColumnName,
                                            final Duration duration,
                                            final List<KeyValue> additionalConditions) {
        final var tables = tableHelper.getTablesForRead(
            metrics.getName(), duration.getStartTimeBucket(), duration.getEndTimeBucket()
        );
        // One table ranks and cuts in SQL. Across day tables the ranking needs each entity's mean over the whole
        // range, so every table returns every entity's summation and sample count, kept exact until the division.
        final Map<String, BigDecimal> summations = new HashMap<>();
        final Map<String, Long> samples = new HashMap<>();
        final boolean oneTable = tables.size() == 1;
        for (final var table : tables) {
            final var sqlAndParameters = oneTable
                ? buildSQL(metrics, valueColumnName, duration, additionalConditions, table)
                : buildSummationSQL(metrics, valueColumnName, duration, additionalConditions, table);

            jdbcClient.executeQuery(sqlAndParameters.sql(), resultSet -> {
                while (resultSet.next()) {
                    final var entityId = resultSet.getString(Metrics.ENTITY_ID);
                    if (oneTable) {
                        summations.put(entityId, resultSet.getBigDecimal("result"));
                        samples.put(entityId, 1L);
                    } else {
                        summations.merge(entityId, resultSet.getBigDecimal("summation"), BigDecimal::add);
                        samples.merge(entityId, resultSet.getLong("samples"), Long::sum);
                    }
                }
                return null;
            }, sqlAndParameters.parameters());
        }

        // Ranked by the exact mean, as the database ranks avg(): comparing summation x the other's samples keeps
        // fractional means apart even where they truncate to the same value. The value returned is the truncated mean.
        final Comparator<String> byMean = (a, b) -> summations.get(a).multiply(BigDecimal.valueOf(samples.get(b)))
                                                              .compareTo(summations.get(b).multiply(
                                                                  BigDecimal.valueOf(samples.get(a))));
        return summations.keySet()
                         .stream()
                         .sorted(Order.ASC.equals(metrics.getOrder()) ? byMean : byMean.reversed())
                         .limit(metrics.getTopN())
                         .map(entityId -> {
                             final var selectedRecord = new SelectedRecord();
                             selectedRecord.setId(entityId);
                             final BigDecimal mean = summations.get(entityId).divide(
                                 BigDecimal.valueOf(samples.get(entityId)), 0, RoundingMode.DOWN);
                             selectedRecord.setValue(String.valueOf(mean.longValue()));
                             return selectedRecord;
                         })
                         .collect(Collectors.toList());
    }

    protected SQLAndParameters buildSQL(
        final TopNCondition metrics,
        final String valueColumnName,
        final Duration duration,
        final List<KeyValue> queries,
        final String table) {

        final var sql = new StringBuilder();
        final var parameters = new ArrayList<>(10);
        sql.append("select result, ").append(Metrics.ENTITY_ID)
           .append(" from (select avg(").append(valueColumnName).append(") as result,")
           .append(Metrics.ENTITY_ID)
           .append(" from ").append(table);
        appendConditions(sql, parameters, metrics, duration, queries);
        sql.append(" group by ").append(Metrics.ENTITY_ID);
        sql.append(")  as T order by result")
           .append(Order.ASC.equals(metrics.getOrder()) ? " asc" : " desc")
           .append(" limit ")
           .append(metrics.getTopN());
        return new SQLAndParameters(sql.toString(), parameters);
    }

    /**
     * The summation and sample count of every entity in one day table, unranked and uncut.
     */
    protected SQLAndParameters buildSummationSQL(
        final TopNCondition metrics,
        final String valueColumnName,
        final Duration duration,
        final List<KeyValue> queries,
        final String table) {

        final var sql = new StringBuilder();
        final var parameters = new ArrayList<>(10);
        sql.append("select sum(").append(valueColumnName).append(") as summation, count(")
           .append(valueColumnName).append(") as samples, ").append(Metrics.ENTITY_ID)
           .append(" from ").append(table);
        appendConditions(sql, parameters, metrics, duration, queries);
        sql.append(" group by ").append(Metrics.ENTITY_ID);
        return new SQLAndParameters(sql.toString(), parameters);
    }

    private void appendConditions(final StringBuilder sql,
                                  final List<Object> parameters,
                                  final TopNCondition metrics,
                                  final Duration duration,
                                  final List<KeyValue> queries) {
        sql.append(" where ")
           .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?")
           .append(" and ")
           .append(Metrics.TIME_BUCKET).append(" >= ? ")
           .append(" and ")
           .append(Metrics.TIME_BUCKET).append(" <= ?");

        parameters.add(metrics.getName());
        parameters.add(duration.getStartTimeBucket());
        parameters.add(duration.getEndTimeBucket());
        if (queries != null) {
            queries.forEach(query -> {
                sql.append(" and ").append(query.getKey()).append(" = ?");
                parameters.add(query.getValue());
            });
        }
        if (CollectionUtils.isNotEmpty(metrics.getAttributes())) {
            metrics.getAttributes().forEach(attr -> {
                if (attr.isEquals()) {
                    sql.append(" and ").append(attr.getKey()).append(" = ?");
                } else {
                    sql.append(" and ").append(attr.getKey()).append(" != ?");
                }
                parameters.add(attr.getValue());
            });
        }
    }
}
