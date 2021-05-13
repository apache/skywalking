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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.event.Event;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

import static com.google.common.base.Strings.isNullOrEmpty;

@Slf4j
@RequiredArgsConstructor
public class H2EventQueryDAO implements IEventQueryDAO {
    private final JDBCHikariCPClient client;

    @Override
    public Events queryEvents(final EventQueryCondition condition) throws Exception {
        final List<String> conditions = new ArrayList<>();
        final List<Object> parameters = new ArrayList<>();

        if (!isNullOrEmpty(condition.getUuid())) {
            conditions.add(Event.UUID + "=?");
            parameters.add(condition.getUuid());
        }

        final Source source = condition.getSource();
        if (source != null) {
            if (!isNullOrEmpty(source.getService())) {
                conditions.add(Event.SERVICE + "=?");
                parameters.add(source.getService());
            }
            if (!isNullOrEmpty(source.getServiceInstance())) {
                conditions.add(Event.SERVICE_INSTANCE + "=?");
                parameters.add(source.getServiceInstance());
            }
            if (!isNullOrEmpty(source.getEndpoint())) {
                conditions.add(Event.ENDPOINT + "=?");
                parameters.add(source.getEndpoint());
            }
        }

        if (!isNullOrEmpty(condition.getName())) {
            conditions.add(Event.NAME + "=?");
            parameters.add(condition.getName());
        }

        if (condition.getType() != null) {
            conditions.add(Event.TYPE + "=?");
            parameters.add(condition.getType().name());
        }

        final Duration time = condition.getTime();
        if (time != null) {
            if (time.getStartTimestamp() > 0) {
                conditions.add(Event.START_TIME + ">?");
                parameters.add(time.getStartTimestamp());
            }
            if (time.getEndTimestamp() > 0) {
                conditions.add(Event.END_TIME + "<?");
                parameters.add(time.getEndTimestamp());
            }
        }

        final String whereClause = conditions.isEmpty() ? "" : conditions.stream().collect(Collectors.joining(" and ", " where ", ""));

        final Events result = new Events();

        try (final Connection connection = client.getConnection()) {
            String sql = "select count(1) total from " + Event.INDEX_NAME + whereClause;
            if (log.isDebugEnabled()) {
                log.debug("Count SQL: {}, parameters: {}", sql, parameters);
            }
            try (final ResultSet resultSet = client.executeQuery(connection, sql, parameters.toArray())) {
                if (!resultSet.next()) {
                    return result;
                }
                result.setTotal(resultSet.getInt("total"));
            }

            sql = "select * from " + Event.INDEX_NAME + whereClause + " limit " + condition.getSize();
            if (log.isDebugEnabled()) {
                log.debug("Query SQL: {}, parameters: {}", sql, parameters);
            }
            try (final ResultSet resultSet = client.executeQuery(connection, sql, parameters.toArray())) {
                while (resultSet.next()) {
                    result.getEvents().add(parseResultSet(resultSet));
                }
            }
        }

        return result;
    }

    protected org.apache.skywalking.oap.server.core.query.type.event.Event parseResultSet(final ResultSet resultSet) throws SQLException {
        final org.apache.skywalking.oap.server.core.query.type.event.Event event = new org.apache.skywalking.oap.server.core.query.type.event.Event();

        event.setUuid(resultSet.getString(Event.UUID));

        final String service = resultSet.getString(Event.SERVICE);
        final String serviceInstance = resultSet.getString(Event.SERVICE_INSTANCE);
        final String endpoint = resultSet.getString(Event.ENDPOINT);

        event.setSource(new Source(service, serviceInstance, endpoint));
        event.setName(resultSet.getString(Event.NAME));
        event.setType(EventType.parse(resultSet.getString(Event.TYPE)));
        event.setMessage(resultSet.getString(Event.MESSAGE));
        event.setParameters(resultSet.getString(Event.PARAMETERS));
        event.setStartTime(resultSet.getLong(Event.START_TIME));
        event.setEndTime(resultSet.getLong(Event.END_TIME));

        return event;
    }
}
