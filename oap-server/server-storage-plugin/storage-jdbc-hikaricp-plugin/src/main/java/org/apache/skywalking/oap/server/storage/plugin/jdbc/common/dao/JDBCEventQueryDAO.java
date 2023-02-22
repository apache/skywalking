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

import io.vavr.Tuple;
import io.vavr.Tuple2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.metrics.Event;
import org.apache.skywalking.oap.server.core.query.PaginationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;

@Slf4j
@RequiredArgsConstructor
public class JDBCEventQueryDAO implements IEventQueryDAO {
    private final JDBCClient jdbcClient;

    @Override
    public Events queryEvents(final EventQueryCondition condition) throws Exception {
        final Tuple2<Stream<String>, Stream<Object>> conditionsParametersPair = buildQuery(condition);
        final Stream<String> conditions = conditionsParametersPair._1();
        final Object[] parameters = conditionsParametersPair._2().toArray();
        final String whereClause = conditions.collect(Collectors.joining(" and ", " where ", ""));


        final Order queryOrder = isNull(condition.getOrder()) ? Order.DES : condition.getOrder();
        final PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());
        String sql = "select * from " + Event.INDEX_NAME + whereClause;
        if (Order.DES.equals(queryOrder)) {
            sql += " order by " + Event.START_TIME + " desc";
        } else {
            sql += " order by " + Event.START_TIME + " asc";
        }
        sql += " limit " + page.getLimit() + " offset " + page.getFrom();
        if (log.isDebugEnabled()) {
            log.debug("Query SQL: {}, parameters: {}", sql, parameters);
        }

        return jdbcClient.executeQuery(sql, resultSet -> {
            final Events result = new Events();
            while (resultSet.next()) {
                result.getEvents().add(parseResultSet(resultSet));
            }
            return result;
        }, parameters);
    }

    @Override
    public Events queryEvents(List<EventQueryCondition> conditions) throws Exception {
        final List<Tuple2<Stream<String>, Stream<Object>>> conditionsParametersPair = conditions.stream()
                                                                                                .map(this::buildQuery)
                                                                                                .collect(Collectors.toList());
        final Object[] parameters = conditionsParametersPair.stream()
                                                            .map(Tuple2::_2)
                                                            .reduce(Stream.empty(), Stream::concat)
                                                            .toArray();
        final String whereClause = conditionsParametersPair.stream()
                                                       .map(Tuple2::_1)
                                                       .map(it -> it.collect(Collectors.joining(" and ")))
                                                       .collect(Collectors.joining(" or ", " where ", ""));

        EventQueryCondition condition = conditions.get(0);
        final Order queryOrder = isNull(condition.getOrder()) ? Order.DES : condition.getOrder();
        final PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());
        String sql = "select * from " + Event.INDEX_NAME + whereClause;
        if (Order.DES.equals(queryOrder)) {
            sql += " order by " + Event.START_TIME + " desc";
        } else {
            sql += " order by " + Event.START_TIME + " asc";
        }
        sql += " limit " + page.getLimit() + " offset " + page.getFrom();
        if (log.isDebugEnabled()) {
            log.debug("Query SQL: {}, parameters: {}", sql, parameters);
        }
        return jdbcClient.executeQuery(sql, resultSet -> {
            final Events result = new Events();

            while (resultSet.next()) {
                result.getEvents().add(parseResultSet(resultSet));
            }

            return result;
        }, parameters);

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
        event.setLayer(Layer.valueOf(resultSet.getInt(Event.LAYER)).name());
        return event;
    }

    protected Tuple2<Stream<String>, Stream<Object>> buildQuery(final EventQueryCondition condition) {
        final Stream.Builder<String> conditions = Stream.builder();
        final Stream.Builder<Object> parameters = Stream.builder();

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

        if (!isNullOrEmpty(condition.getLayer())) {
            conditions.add(Event.LAYER + "=?");
            parameters.add(String.valueOf(Layer.nameOf(condition.getLayer()).value()));
        }

        return Tuple.of(conditions.build(), parameters.build());
    }
}
