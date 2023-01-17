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

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

import static java.util.Objects.nonNull;

@RequiredArgsConstructor
public class JDBCTagAutoCompleteQueryDAO implements ITagAutoCompleteQueryDAO {
    private final JDBCHikariCPClient jdbcClient;

    @Override
    public Set<String> queryTagAutocompleteKeys(final TagType tagType,
                                                final int limit,
                                                final Duration duration) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(2);

        sql.append("select distinct ").append(TagAutocompleteData.TAG_KEY).append(" from ")
           .append(TagAutocompleteData.INDEX_NAME).append(" where ");
        sql.append(" 1=1 ");
        appendTagAutocompleteCondition(tagType, duration, sql, condition);
        sql.append(" limit ").append(limit);
        try (Connection connection = jdbcClient.getConnection()) {
            ResultSet resultSet = jdbcClient.executeQuery(connection, sql.toString(), condition.toArray(new Object[0]));
            Set<String> tagKeys = new HashSet<>();
            while (resultSet.next()) {
                tagKeys.add(resultSet.getString(TagAutocompleteData.TAG_KEY));
            }
            return tagKeys;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Set<String> queryTagAutocompleteValues(final TagType tagType,
                                                  final String tagKey,
                                                  final int limit,
                                                  final Duration duration) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(3);
        sql.append("select * from ").append(TagAutocompleteData.INDEX_NAME).append(" where ");
        sql.append(TagAutocompleteData.TAG_KEY).append(" = ?");
        condition.add(tagKey);
        appendTagAutocompleteCondition(tagType, duration, sql, condition);
        sql.append(" limit ").append(limit);

        try (Connection connection = jdbcClient.getConnection()) {
            ResultSet resultSet = jdbcClient.executeQuery(connection, sql.toString(), condition.toArray(new Object[0]));
            Set<String> tagValues = new HashSet<>();
            while (resultSet.next()) {
                tagValues.add(resultSet.getString(TagAutocompleteData.TAG_VALUE));
            }
            return tagValues;
        } catch (SQLException e) {
            throw new IOException(e);
        }
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
