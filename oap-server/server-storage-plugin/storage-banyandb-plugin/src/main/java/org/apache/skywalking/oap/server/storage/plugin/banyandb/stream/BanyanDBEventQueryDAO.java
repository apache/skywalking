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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.query.PaginationUtils;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.source.Event;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.source.Event} is a stream
 */
public class BanyanDBEventQueryDAO extends AbstractBanyanDBDAO implements IEventQueryDAO {
    public BanyanDBEventQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public Events queryEvents(EventQueryCondition condition) throws Exception {
        StreamQueryResponse resp = query(Event.INDEX_NAME,
                ImmutableList.of(Event.UUID, Event.SERVICE, Event.SERVICE_INSTANCE, Event.ENDPOINT, Event.NAME, Event.TYPE, Event.START_TIME, Event.END_TIME),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(ImmutableList.of(Event.MESSAGE, Event.PARAMETERS));

                        buildConditions(condition, query);

                        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());
                        query.setLimit(page.getLimit());
                        query.setOffset(page.getFrom());
                        switch (condition.getOrder()) {
                            case ASC:
                                query.setOrderBy(new StreamQuery.OrderBy("start_time", StreamQuery.OrderBy.Type.ASC));
                                break;
                            case DES:
                                query.setOrderBy(new StreamQuery.OrderBy("start_time", StreamQuery.OrderBy.Type.DESC));
                        }
                    }

                    private void buildConditions(EventQueryCondition condition, final StreamQuery query) {
                        if (!Strings.isNullOrEmpty(condition.getUuid())) {
                            query.appendCondition(eq(Event.UUID, condition.getUuid()));
                        }
                        final Source source = condition.getSource();
                        if (source != null) {
                            if (!Strings.isNullOrEmpty(source.getService())) {
                                query.appendCondition(eq(Event.SERVICE, source.getService()));
                            }
                            if (!Strings.isNullOrEmpty(source.getServiceInstance())) {
                                query.appendCondition(eq(Event.SERVICE_INSTANCE, source.getServiceInstance()));
                            }
                            if (!Strings.isNullOrEmpty(source.getEndpoint())) {
                                query.appendCondition(eq(Event.ENDPOINT, source.getEndpoint()));
                            }
                        }
                        if (!Strings.isNullOrEmpty(condition.getName())) {
                            query.appendCondition(eq(Event.NAME, condition.getName()));
                        }
                        if (condition.getType() != null) {
                            query.appendCondition(eq(Event.TYPE, condition.getType().name()));
                        }
                        final Duration time = condition.getTime();
                        if (time != null) {
                            if (time.getStartTimestamp() > 0) {
                                query.appendCondition(gte(Event.START_TIME, time.getStartTimestamp()));
                            }
                            if (time.getEndTimestamp() > 0) {
                                query.appendCondition(lte(Event.END_TIME, time.getEndTimestamp()));
                            }
                        }
                    }
                });

        List<org.apache.skywalking.oap.server.core.query.type.event.Event> eventList = resp.getElements().stream().map(new EventDeserializer()).collect(Collectors.toList());

        Events events = new Events();
        events.setEvents(eventList);
        events.setTotal(eventList.size());
        return events;
    }

    @Override
    public Events queryEvents(List<EventQueryCondition> conditionList) throws Exception {
        Events events = new Events();
        for (final EventQueryCondition condition : conditionList) {
            Events subEvents = this.queryEvents(condition);
            if (subEvents.getEvents().size() == 0) {
                continue;
            }

            events.getEvents().addAll(subEvents.getEvents());
            events.setTotal(events.getTotal() + subEvents.getTotal());
        }

        return events;
    }

    public static class EventDeserializer implements RowEntityDeserializer<org.apache.skywalking.oap.server.core.query.type.event.Event> {
        @Override
        public org.apache.skywalking.oap.server.core.query.type.event.Event apply(RowEntity row) {
            final org.apache.skywalking.oap.server.core.query.type.event.Event resultEvent = new org.apache.skywalking.oap.server.core.query.type.event.Event();
            // searchable
            final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
            resultEvent.setUuid((String) searchable.get(0).getValue());
            resultEvent.setSource(new Source((String) searchable.get(1).getValue(), (String) searchable.get(2).getValue(), (String) searchable.get(3).getValue()));
            resultEvent.setName((String) searchable.get(4).getValue());
            resultEvent.setType(EventType.parse((String) searchable.get(5).getValue()));
            resultEvent.setStartTime(((Number) searchable.get(6).getValue()).longValue());
            resultEvent.setEndTime(((Number) searchable.get(7).getValue()).longValue());
            // data
            final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
            resultEvent.setMessage((String) data.get(0).getValue());
            resultEvent.setParameters((String) data.get(1).getValue());
            return resultEvent;
        }
    }
}
