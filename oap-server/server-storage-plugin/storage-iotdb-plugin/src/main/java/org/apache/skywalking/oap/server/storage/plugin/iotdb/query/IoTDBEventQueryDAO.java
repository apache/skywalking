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
import org.apache.skywalking.oap.server.core.query.PaginationUtils;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.source.Event;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

import java.util.List;

public class IoTDBEventQueryDAO implements IEventQueryDAO {
    private final IoTDBClient client;

    public IoTDBEventQueryDAO(IoTDBClient client) {
        this.client = client;
    }

    @Override
    public Events queryEvents(EventQueryCondition condition) throws Exception {
        final Events events = new Events();
        StringBuilder query = new StringBuilder();
        query.append(String.format("select count(%s) from %s", Event.UUID,
                client.getStorageGroup() + IoTDBClient.DOT + Event.INDEX_NAME));
        query.append(whereSQL(condition, query));
        int total = client.queryWithAgg(Event.INDEX_NAME, query.toString());
        events.setTotal(total);

        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());
        query = new StringBuilder();
        query.append(String.format("select * from %s", client.getStorageGroup() + IoTDBClient.DOT + Event.INDEX_NAME));
        query.append(whereSQL(condition, query))
                .append(" limit ").append(page.getLimit()).append(" offset ").append(page.getFrom());
        List<? super StorageData> storageDataList = client.queryForList(Event.INDEX_NAME, query.toString(), new Event.Builder());
        storageDataList.forEach(storageData -> {
            Event event = (Event) storageData;
            events.getEvents().add(parseEvent(event));
        });
        return events;
    }

    @Override
    public Events queryEvents(List<EventQueryCondition> conditionList) throws Exception {
        final Events events = new Events();
        StringBuilder query = new StringBuilder();
        query.append(String.format("select count(%s) from %s", Event.UUID,
                client.getStorageGroup() + IoTDBClient.DOT + Event.INDEX_NAME));
        query.append(whereSQL(conditionList, query));
        int total = client.queryWithAgg(Event.INDEX_NAME, query.toString());
        events.setTotal(total);

        final int size = conditionList.stream().map(EventQueryCondition::getPaging)
                .mapToInt(Pagination::getPageSize).sum();
        query = new StringBuilder();
        query.append(String.format("select * from %s", client.getStorageGroup() + IoTDBClient.DOT + Event.INDEX_NAME));
        query.append(whereSQL(conditionList, query))
                .append(" limit ").append(size);
        List<? super StorageData> storageDataList = client.queryForList(Event.INDEX_NAME, query.toString(), new Event.Builder());
        storageDataList.forEach(storageData -> {
            Event event = (Event) storageData;
            events.getEvents().add(parseEvent(event));
        });
        return events;
    }

    private StringBuilder whereSQL(final EventQueryCondition condition, StringBuilder query) {
        query.append(" where 1=1 ");
        if (!Strings.isNullOrEmpty(condition.getUuid())) {
            query.append(" and ").append(Event.UUID).append(" = '").append(condition.getUuid()).append("'");
        }
        final Source source = condition.getSource();
        if (source != null) {
            if (!Strings.isNullOrEmpty(source.getService())) {
                query.append(" and ").append(Event.SERVICE).append(" = '").append(source.getService()).append("'");
            }
            if (!Strings.isNullOrEmpty(source.getServiceInstance())) {
                query.append(" and ").append(Event.SERVICE_INSTANCE).append(" = '").append(source.getServiceInstance()).append("'");
            }
            if (!Strings.isNullOrEmpty(source.getService())) {
                query.append(" and ").append(Event.ENDPOINT).append(" = '").append(source.getEndpoint()).append("'");
            }
        }
        if (!Strings.isNullOrEmpty(condition.getName())) {
            query.append(" and ").append(Event.NAME).append(" = '").append(condition.getName()).append("'");
        }
        if (condition.getType() != null) {
            query.append(" and ").append(Event.TYPE).append(" = '").append(condition.getType().name()).append("'");
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
        return query;
    }

    private StringBuilder whereSQL(final List<EventQueryCondition> conditions, StringBuilder query) {
        query.append(" where 1=1");
        conditions.forEach(condition -> {
            query.append("or (1=1 ");
            if (!Strings.isNullOrEmpty(condition.getUuid())) {
                query.append(" and ").append(Event.UUID).append(" = '").append(condition.getUuid()).append("'");
            }
            final Source source = condition.getSource();
            if (source != null) {
                if (!Strings.isNullOrEmpty(source.getService())) {
                    query.append(" and ").append(Event.SERVICE).append(" = '").append(source.getService()).append("'");
                }
                if (!Strings.isNullOrEmpty(source.getServiceInstance())) {
                    query.append(" and ").append(Event.SERVICE_INSTANCE).append(" = '").append(source.getServiceInstance()).append("'");
                }
                if (!Strings.isNullOrEmpty(source.getService())) {
                    query.append(" and ").append(Event.ENDPOINT).append(" = '").append(source.getEndpoint()).append("'");
                }
            }
            if (!Strings.isNullOrEmpty(condition.getName())) {
                query.append(" and ").append(Event.NAME).append(" = '").append(condition.getName()).append("'");
            }
            if (condition.getType() != null) {
                query.append(" and ").append(Event.TYPE).append(" = '").append(condition.getType().name()).append("'");
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
            query.append(")");
        });
        return query;
    }

    private org.apache.skywalking.oap.server.core.query.type.event.Event parseEvent(final Event event) {
        final org.apache.skywalking.oap.server.core.query.type.event.Event eventQuery = new org.apache.skywalking.oap.server.core.query.type.event.Event();
        eventQuery.setUuid(event.getUuid());
        eventQuery.setSource(new Source(event.getService(), event.getServiceInstance(), event.getEndpoint()));
        eventQuery.setName(event.getName());
        eventQuery.setType(EventType.parse(event.getType()));
        eventQuery.setMessage(event.getMessage());
        eventQuery.setParameters(event.getParameters());
        eventQuery.setStartTime(event.getStartTime());
        eventQuery.setEndTime(event.getEndTime());
        return eventQuery;
    }
}
