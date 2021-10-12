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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import com.google.common.base.Strings;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.query.PaginationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.source.Event;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

@Slf4j
@RequiredArgsConstructor
public class IoTDBEventQueryDAO implements IEventQueryDAO {
    private final IoTDBClient client;
    private final StorageHashMapBuilder<Event> storageBuilder = new Event.Builder();

    @Override
    public Events queryEvents(EventQueryCondition condition) throws Exception {
        // This method maybe have poor efficiency. It queries all data which meets a condition without select function.
        // https://github.com/apache/iotdb/discussions/3888
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, Event.INDEX_NAME);
        query = client.addQueryAsterisk(Event.INDEX_NAME, query);
        query = whereSQL(condition, query);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(Event.INDEX_NAME, query.toString(), storageBuilder);
        final Events events = new Events();
        int limitCount = 0;
        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());
        for (int i = 0; i < storageDataList.size(); i++) {
            if (i >= page.getFrom() && limitCount < page.getLimit()) {
                limitCount++;
                Event event = (Event) storageDataList.get(i);
                events.getEvents().add(parseEvent(event));
            }
        }
        events.setTotal(storageDataList.size());
        // resort by self, because of the select query result order by time.
        final Order order = Objects.isNull(condition.getOrder()) ? Order.DES : condition.getOrder();
        if (Order.DES.equals(order)) {
            events.getEvents().sort(
                    (org.apache.skywalking.oap.server.core.query.type.event.Event e1,
                     org.apache.skywalking.oap.server.core.query.type.event.Event e2)
                            -> Long.compare(e2.getStartTime(), e1.getStartTime()));
        } else {
            events.getEvents().sort(
                    Comparator.comparingLong(org.apache.skywalking.oap.server.core.query.type.event.Event::getStartTime));
        }
        return events;
    }

    @Override
    public Events queryEvents(List<EventQueryCondition> conditionList) throws Exception {
        // This method maybe have poor efficiency. It queries all data which meets a condition without select function.
        // https://github.com/apache/iotdb/discussions/3888
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, Event.INDEX_NAME);
        query = client.addQueryAsterisk(Event.INDEX_NAME, query);
        query = whereSQL(conditionList, query);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(Event.INDEX_NAME, query.toString(), storageBuilder);
        final Events events = new Events();
        EventQueryCondition condition = conditionList.get(0);
        int limitCount = 0;
        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());
        log.debug("page from: {}, limit: {}", page.getFrom(), page.getLimit());
        for (int i = 0; i < storageDataList.size(); i++) {
            if (i >= page.getFrom() && limitCount < page.getLimit()) {
                limitCount++;
                Event event = (Event) storageDataList.get(i);
                log.debug("!!!! get event, its uuid: {}", event.getUuid());
                events.getEvents().add(parseEvent(event));
            }
        }
        events.setTotal(storageDataList.size());
        log.debug("!!!!events size: {}", events.getTotal());
        // resort by self, because of the select query result order by time.
        final Order order = Objects.isNull(condition.getOrder()) ? Order.DES : condition.getOrder();
        if (Order.DES.equals(order)) {
            events.getEvents().sort(
                    (org.apache.skywalking.oap.server.core.query.type.event.Event e1,
                     org.apache.skywalking.oap.server.core.query.type.event.Event e2)
                            -> Long.compare(e2.getStartTime(), e1.getStartTime()));
        } else {
            events.getEvents().sort(
                    Comparator.comparingLong(org.apache.skywalking.oap.server.core.query.type.event.Event::getStartTime));
        }
        return events;
    }

    private StringBuilder whereSQL(final EventQueryCondition condition, StringBuilder query) {
        query.append(" where 1=1");
        if (!Strings.isNullOrEmpty(condition.getUuid())) {
            query.append(" and ").append(Event.UUID).append(" = \"").append(condition.getUuid()).append("\"");
        }
        final Source source = condition.getSource();
        if (source != null) {
            if (!Strings.isNullOrEmpty(source.getService())) {
                query.append(" and ").append(Event.SERVICE).append(" = \"").append(source.getService()).append("\"");
            }
            if (!Strings.isNullOrEmpty(source.getServiceInstance())) {
                query.append(" and ").append(Event.SERVICE_INSTANCE).append(" = \"").append(source.getServiceInstance()).append("\"");
            }
            if (!Strings.isNullOrEmpty(source.getEndpoint())) {
                query.append(" and ").append(Event.ENDPOINT).append(" = \"").append(source.getEndpoint()).append("\"");
            }
        }
        if (!Strings.isNullOrEmpty(condition.getName())) {
            query.append(" and ").append(Event.NAME).append(" = \"").append(condition.getName()).append("\"");
        }
        if (condition.getType() != null) {
            query.append(" and ").append(Event.TYPE).append(" = \"").append(condition.getType().name()).append("\"");
        }
        final Duration time = condition.getTime();
        if (time != null) {
            if (time.getStartTimestamp() > 0) {
                query.append(" and ").append(Event.START_TIME).append(" > ").append(time.getStartTimestamp());
            }
            if (time.getEndTimestamp() > 0) {
                query.append(" and ").append(Event.END_TIME).append(" < ").append(time.getEndTimestamp());
            }
        }
        // IoTDB doesn't support the query contains "1=1" and "*" at the meantime.
        String queryString = query.toString();
        queryString = queryString.replace("1=1 and ", "");
        return new StringBuilder(queryString);
    }

    private StringBuilder whereSQL(final List<EventQueryCondition> conditions, StringBuilder query) {
        query.append(" where 1=1");
        for (EventQueryCondition condition : conditions) {
            query.append(" or (");
            query = whereSQL(condition, query);
            query.append(")");
        }
        String queryString = query.toString();
        queryString = queryString.replace("( where ", "(");
        queryString = queryString.replace("1=1 or ", "");
        return new StringBuilder(queryString);
    }

    private org.apache.skywalking.oap.server.core.query.type.event.Event parseEvent(final Event event) {
        final org.apache.skywalking.oap.server.core.query.type.event.Event resultEvent = new org.apache.skywalking.oap.server.core.query.type.event.Event();
        resultEvent.setUuid(event.getUuid());
        resultEvent.setSource(new Source(event.getService(), event.getServiceInstance(), event.getEndpoint()));
        resultEvent.setName(event.getName());
        resultEvent.setType(EventType.parse(event.getType()));
        resultEvent.setMessage(event.getMessage());
        resultEvent.setParameters(event.getParameters());
        resultEvent.setStartTime(event.getStartTime());
        resultEvent.setEndTime(event.getEndTime());
        log.debug("resultEvent: {}", resultEvent);
        return resultEvent;
    }
}
