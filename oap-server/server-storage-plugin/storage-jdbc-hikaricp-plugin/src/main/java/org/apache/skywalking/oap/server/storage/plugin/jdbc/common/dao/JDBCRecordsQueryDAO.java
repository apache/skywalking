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
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.storage.query.IRecordsQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class JDBCRecordsQueryDAO implements IRecordsQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<Record> readRecords(final RecordCondition condition,
                                    final String valueColumnName,
                                    final Duration duration) {
        final var tables = tableHelper.getTablesForRead(
            condition.getName(),
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        );
        final var results = new ArrayList<Record>();

        for (String table : tables) {
            final var sqlAndParameters = buildSQL(condition, valueColumnName, duration, table);
            jdbcClient.executeQuery(
                sqlAndParameters.sql(),
                resultSet -> {
                    while (resultSet.next()) {
                        Record record = new Record();
                        record.setName(resultSet.getString(TopN.STATEMENT));
                        final String refId = resultSet.getString(TopN.TRACE_ID);
                        record.setRefId(StringUtil.isEmpty(refId) ? "" : refId);
                        record.setId(record.getRefId());
                        record.setValue(resultSet.getInt(valueColumnName));
                        results.add(record);
                    }
                    return null;
                },
                sqlAndParameters.parameters());
        }

        return results;
    }

    protected static SQLAndParameters buildSQL(
        RecordCondition condition,
        String valueColumnName,
        Duration duration,
        String table) {
        StringBuilder sql = new StringBuilder("select * from " + table + " where ");
        List<Object> parameters = new ArrayList<>(10);
        sql.append(" ").append(TopN.ENTITY_ID).append(" = ? and");
        parameters.add(condition.getParentEntity().buildId());
        sql.append(" ").append(TopN.TIME_BUCKET).append(" >= ?");
        parameters.add(duration.getStartTimeBucketInSec());
        sql.append(" and ").append(TopN.TIME_BUCKET).append(" <= ?");
        parameters.add(duration.getEndTimeBucketInSec());

        sql.append(" order by ").append(valueColumnName);
        if (condition.getOrder().equals(Order.DES)) {
            sql.append(" desc ");
        } else {
            sql.append(" asc ");
        }
        sql.append(" limit ").append(condition.getTopN());

        return new SQLAndParameters(sql.toString(), parameters);
    }
}
