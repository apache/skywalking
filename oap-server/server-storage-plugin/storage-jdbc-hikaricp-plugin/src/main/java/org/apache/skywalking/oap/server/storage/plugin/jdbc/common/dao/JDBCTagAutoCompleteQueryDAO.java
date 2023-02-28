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
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Objects.nonNull;

@RequiredArgsConstructor
public class JDBCTagAutoCompleteQueryDAO implements ITagAutoCompleteQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public Set<String> queryTagAutocompleteKeys(final TagType tagType,
                                                final int limit,
                                                final Duration duration) {
        final var tables = tableHelper.getTablesForRead(
            TagAutocompleteData.INDEX_NAME,
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        );
        final var results = new HashSet<String>();

        for (String table : tables) {
            final var sqlAndParameters = buildSQLForQueryKeys(tagType, limit, duration, table);
            jdbcClient.executeQuery(
                sqlAndParameters.sql(),
                resultSet -> {
                    while (resultSet.next()) {
                        results.add(resultSet.getString(TagAutocompleteData.TAG_KEY));
                    }
                    return null;
                },
                sqlAndParameters.parameters());
        }
        return results;
    }

    protected SQLAndParameters buildSQLForQueryKeys(TagType tagType, int limit, Duration duration, String table) {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(2);

        sql.append("select distinct ").append(TagAutocompleteData.TAG_KEY).append(" from ")
           .append(table).append(" where ");
        sql.append(" 1=1 ");
        appendTagAutocompleteCondition(tagType, duration, sql, condition);
        sql.append(" limit ").append(limit);

        return new SQLAndParameters(sql.toString(), condition);
    }

    @Override
    @SneakyThrows
    public Set<String> queryTagAutocompleteValues(final TagType tagType,
                                                  final String tagKey,
                                                  final int limit,
                                                  final Duration duration) {
        final var tables = tableHelper.getTablesForRead(
            TagAutocompleteData.INDEX_NAME,
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        );
        final var results = new HashSet<String>();

        for (String table : tables) {
            final var sqlAndParameters = buildSQLForQueryValues(tagType, tagKey, limit, duration, table);
            jdbcClient.executeQuery(
                sqlAndParameters.sql(),
                resultSet -> {
                    while (resultSet.next()) {
                        results.add(resultSet.getString(TagAutocompleteData.TAG_VALUE));
                    }
                    return null;
                },
                sqlAndParameters.parameters()
            );
        }
        return results;
    }

    protected SQLAndParameters buildSQLForQueryValues(TagType tagType, String tagKey, int limit, Duration duration, String table) {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(3);
        sql.append("select * from ").append(table).append(" where ");
        sql.append(TagAutocompleteData.TAG_KEY).append(" = ?");
        condition.add(tagKey);
        appendTagAutocompleteCondition(tagType, duration, sql, condition);
        sql.append(" limit ").append(limit);

        return new SQLAndParameters(sql.toString(), condition);
    }

    private void appendTagAutocompleteCondition(final TagType tagType,
                                                final Duration duration,
                                                final StringBuilder sql,
                                                final List<Object> condition) {
        long startSecondTB = 0;
        long endSecondTB = 0;
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
        }

        long startTB = startSecondTB / 1000000 * 10000;
        long endTB = endSecondTB / 1000000 * 10000 + 2359;

        sql.append(" and ");
        sql.append(TagAutocompleteData.TAG_TYPE).append(" = ?");
        condition.add(tagType.name());

        if (startTB > 0) {
            sql.append(" and ");
            sql.append(TagAutocompleteData.TIME_BUCKET).append(">=?");
            condition.add(startTB);
        }
        if (endTB > 0) {
            sql.append(" and ");
            sql.append(TagAutocompleteData.TIME_BUCKET).append("<=?");
            condition.add(endTB);
        }
    }
}
