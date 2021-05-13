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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.event.Event;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants.ALL_FIELDS;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gt;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lt;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
@RequiredArgsConstructor
public class EventQueryDAO implements IEventQueryDAO {
    private final InfluxClient client;

    @Override
    public Events queryEvents(final EventQueryCondition condition) throws Exception {
        final WhereQueryImpl<SelectQueryImpl> recallQuery = buildQuery(condition);

        final SelectQueryImpl countQuery = select().count(Event.UUID).from(client.getDatabase(), Event.INDEX_NAME);
        recallQuery.getClauses().forEach(countQuery::where);

        final Query query = new Query(countQuery.getCommand() + recallQuery.getCommand());
        final List<QueryResult.Result> results = client.query(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {}", query.getCommand());
            log.debug("Result: {}", results);
        }
        if (results.size() != 2) {
            throw new IOException("Expecting to get 2 Results, but it is " + results.size());
        }

        final QueryResult.Series counterSeries = results.get(0).getSeries().get(0);
        final List<QueryResult.Series> recallSeries = results.get(1).getSeries();

        final Events events = new Events();

        events.setTotal(((Number) counterSeries.getValues().get(0).get(1)).longValue());

        recallSeries.forEach(
            series -> series.getValues().forEach(
                values -> events.getEvents().add(parseSeriesValues(series, values))
            )
        );

        return events;
    }

    protected org.apache.skywalking.oap.server.core.query.type.event.Event parseSeriesValues(final QueryResult.Series series, final List<Object> values) {
        final org.apache.skywalking.oap.server.core.query.type.event.Event event = new org.apache.skywalking.oap.server.core.query.type.event.Event();

        final List<String> columns = series.getColumns();
        final Map<String, Object> data = new HashMap<>();

        for (int i = 1; i < columns.size(); i++) {
            Object value = values.get(i);
            if (value instanceof StorageDataComplexObject) {
                value = ((StorageDataComplexObject) value).toStorageData();
            }
            data.put(columns.get(i), value);
        }
        event.setUuid((String) data.get(Event.UUID));

        final String service = (String) data.get(Event.SERVICE);
        final String serviceInstance = (String) data.get(Event.SERVICE_INSTANCE);
        final String endpoint = (String) data.get(Event.ENDPOINT);

        event.setSource(new Source(service, serviceInstance, endpoint));
        event.setName((String) data.get(Event.NAME));
        event.setType(EventType.parse((String) data.get(Event.TYPE)));
        event.setMessage((String) data.get(Event.MESSAGE));
        event.setParameters((String) data.get(Event.PARAMETERS));
        event.setStartTime(((Number) data.get(Event.START_TIME)).longValue());
        event.setEndTime(((Number) data.get(Event.END_TIME)).longValue());

        return event;
    }

    protected WhereQueryImpl<SelectQueryImpl> buildQuery(final EventQueryCondition condition) {
        final String topFunc = Order.DES.equals(condition.getOrder()) ? InfluxConstants.SORT_DES : InfluxConstants.SORT_ASC;
        final WhereQueryImpl<SelectQueryImpl> query =
            select().raw(ALL_FIELDS)
                    .function(topFunc, Event.START_TIME, condition.getSize())
                    .from(client.getDatabase(), Event.INDEX_NAME)
                    .where();

        if (!isNullOrEmpty(condition.getUuid())) {
            query.and(eq(Event.UUID, condition.getUuid()));
        }

        final Source source = condition.getSource();
        if (source != null) {
            if (!isNullOrEmpty(source.getService())) {
                query.and(eq(Event.SERVICE, source.getService()));
            }
            if (!isNullOrEmpty(source.getServiceInstance())) {
                query.and(eq(Event.SERVICE_INSTANCE, source.getServiceInstance()));
            }
            if (!isNullOrEmpty(source.getEndpoint())) {
                query.and(contains(Event.ENDPOINT, source.getEndpoint().replaceAll("/", "\\\\/")));
            }
        }

        if (!isNullOrEmpty(condition.getName())) {
            query.and(eq(InfluxConstants.NAME, condition.getName()));
        }

        if (condition.getType() != null) {
            query.and(eq(Event.TYPE, condition.getType().name()));
        }

        final Duration startTime = condition.getTime();
        if (startTime != null) {
            if (startTime.getStartTimestamp() > 0) {
                query.and(gt(Event.START_TIME, startTime.getStartTimestamp()));
            }
            if (startTime.getEndTimestamp() > 0) {
                query.and(lt(Event.END_TIME, startTime.getEndTimestamp()));
            }
        }

        return query;
    }
}
