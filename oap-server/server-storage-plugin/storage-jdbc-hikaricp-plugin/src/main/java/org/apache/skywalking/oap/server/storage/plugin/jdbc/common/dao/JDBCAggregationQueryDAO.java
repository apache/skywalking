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
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class JDBCAggregationQueryDAO implements IAggregationQueryDAO {
    protected final JDBCHikariCPClient jdbcClient;

    @Override
    public List<SelectedRecord> sortMetrics(final TopNCondition metrics,
                                            final String valueColumnName,
                                            final Duration duration,
                                            List<KeyValue> additionalConditions) throws IOException {
        List<Object> conditions = new ArrayList<>(10);
        StringBuilder sql = buildMetricsValueSql(valueColumnName, metrics.getName());
        sql.append(Metrics.TIME_BUCKET).append(" >= ? and ").append(Metrics.TIME_BUCKET).append(" <= ?");
        conditions.add(duration.getStartTimeBucket());
        conditions.add(duration.getEndTimeBucket());
        if (additionalConditions != null) {
            additionalConditions.forEach(condition -> {
                sql.append(" and ").append(condition.getKey()).append("=?");
                conditions.add(condition.getValue());
            });
        }
        sql.append(" group by ").append(Metrics.ENTITY_ID);
        sql.append(")  as T order by result")
           .append(metrics.getOrder().equals(Order.ASC) ? " asc" : " desc")
           .append(" limit ")
           .append(metrics.getTopN());

        return jdbcClient.executeQuery(sql.toString(), resultSet -> {
            final var topNEntities = new ArrayList<SelectedRecord>();
            while (resultSet.next()) {
                final var topNEntity = new SelectedRecord();
                topNEntity.setId(resultSet.getString(Metrics.ENTITY_ID));
                topNEntity.setValue(resultSet.getString("result"));
                topNEntities.add(topNEntity);
            }
            return topNEntities;
        }, conditions.toArray(new Object[0]));
    }

    protected StringBuilder buildMetricsValueSql(String valueColumnName, String metricsName) {
        StringBuilder sql = new StringBuilder();
        sql.append("select result,")
           .append(Metrics.ENTITY_ID)
           .append(" from (select avg(")
           .append(valueColumnName)
           .append(") result,")
           .append(Metrics.ENTITY_ID)
           .append(" from ")
           .append(metricsName)
           .append(" where ");
        return sql;
    }
}
