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
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;

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
        final var results = new ArrayList<SelectedRecord>();
        final var tables = tableHelper.getTablesForRead(
            metrics.getName(), duration.getStartTimeBucket(), duration.getEndTimeBucket()
        );

        for (final var table : tables) {
            final var sqlAndParameters = buildSQL(metrics, valueColumnName, duration, additionalConditions, table);

            jdbcClient.executeQuery(sqlAndParameters.sql(), resultSet -> {
                while (resultSet.next()) {
                    final var topNEntity = new SelectedRecord();
                    topNEntity.setId(resultSet.getString(Metrics.ENTITY_ID));
                    topNEntity.setValue(String.valueOf(resultSet.getInt("result")));
                    results.add(topNEntity);
                }
                return null;
            }, sqlAndParameters.parameters());
        }

        final var comparator =
            Order.ASC.equals(metrics.getOrder()) ?
                comparing((SelectedRecord it) -> Long.parseLong(it.getValue())) :
                comparing((SelectedRecord it) -> Long.parseLong(it.getValue())).reversed();
        return results
            .stream()
            .collect(groupingBy(SelectedRecord::getId))
            .entrySet()
            .stream()
            .map(entry -> {
                final var selectedRecord = new SelectedRecord();
                final var average = (int) entry.getValue().stream().map(SelectedRecord::getValue).mapToLong(Long::parseLong).average().orElse(0);
                selectedRecord.setId(entry.getKey());
                selectedRecord.setValue(String.valueOf(average));
                return selectedRecord;
            })
            .sorted(comparator)
            .limit(metrics.getTopN())
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
           .append(" from ").append(table)
           .append(" where ")
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
            for (int i = 0; i < metrics.getAttributes().length; i++) {
                if (StringUtil.isNotEmpty(metrics.getAttributes()[i])) {
                    sql.append(" and ").append(Metrics.ATTR_NAME_PREFIX).append(i).append(" = ?");
                    parameters.add(metrics.getAttributes()[i]);
                }
            }
        }
        sql.append(" group by ").append(Metrics.ENTITY_ID);
        sql.append(")  as T order by result")
           .append(Order.ASC.equals(metrics.getOrder()) ? " asc" : " desc")
           .append(" limit ")
           .append(metrics.getTopN());
        return new SQLAndParameters(sql.toString(), parameters);
    }
}
