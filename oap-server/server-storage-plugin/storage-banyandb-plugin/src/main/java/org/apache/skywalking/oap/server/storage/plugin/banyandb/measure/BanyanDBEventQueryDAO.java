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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.measure;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.query.PaginationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.analysis.metrics.Event;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;

public class BanyanDBEventQueryDAO extends AbstractBanyanDBDAO implements IEventQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(
            Event.UUID, Event.SERVICE, Event.SERVICE_INSTANCE, Event.ENDPOINT, Event.NAME,
            Event.MESSAGE, Event.TYPE, Event.START_TIME, Event.END_TIME, Event.PARAMETERS, Event.LAYER);

    public BanyanDBEventQueryDAO(final BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public Events queryEvents(EventQueryCondition condition) throws Exception {
        MeasureQueryResponse resp = query(Event.INDEX_NAME, TAGS,
                Collections.emptySet(), new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
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
                                query.and(eq(Event.ENDPOINT, source.getEndpoint()));
                            }
                        }

                        if (!isNullOrEmpty(condition.getName())) {
                            query.and(eq(Event.NAME, condition.getName()));
                        }

                        if (condition.getType() != null) {
                            query.and(eq(Event.TYPE, condition.getType().name()));
                        }

                        final Duration startTime = condition.getTime();
                        if (startTime != null) {
                            if (startTime.getStartTimestamp() > 0) {
                                query.and(gte(Event.START_TIME, startTime.getStartTimestamp()));
                            }
                            if (startTime.getEndTimestamp() > 0) {
                                query.and(lte(Event.END_TIME, startTime.getEndTimestamp()));
                            }
                        }

                        if (!isNullOrEmpty(condition.getLayer())) {
                            query.and(eq(Event.LAYER, Layer.valueOf(condition.getLayer()).value()));
                        }
                    }
                });
        Events events = new Events();
        if (resp.size() == 0) {
            return events;
        }
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            events.getEvents().add(buildEventView(dataPoint));
        }
        sortEvents(events, condition);
        return events;
    }

    @Override
    public Events queryEvents(List<EventQueryCondition> conditionList) throws Exception {
        Events totalEvents = new Events();
        for (final EventQueryCondition cond : conditionList) {
            final Events singleEvents = this.queryEvents(cond);
            totalEvents.getEvents().addAll(singleEvents.getEvents());
        }
        return totalEvents;
    }

    protected org.apache.skywalking.oap.server.core.query.type.event.Event buildEventView(
            final DataPoint dataPoint) {
        final org.apache.skywalking.oap.server.core.query.type.event.Event event =
                new org.apache.skywalking.oap.server.core.query.type.event.Event();

        event.setUuid(dataPoint.getTagValue(Event.UUID));

        String service = dataPoint.getTagValue(Event.SERVICE);
        String serviceInstance = dataPoint.getTagValue(Event.SERVICE_INSTANCE);
        String endpoint = dataPoint.getTagValue(Event.ENDPOINT);
        event.setSource(new Source(service, serviceInstance, endpoint));

        event.setName(dataPoint.getTagValue(Event.NAME));
        event.setType(EventType.parse(dataPoint.getTagValue(Event.TYPE)));
        event.setMessage(dataPoint.getTagValue(Event.MESSAGE));
        event.setParameters((String) dataPoint.getTagValue(Event.PARAMETERS));
        event.setStartTime(dataPoint.getTagValue(Event.START_TIME));
        event.setEndTime(dataPoint.getTagValue(Event.END_TIME));

        event.setLayer(Layer.valueOf(((Number) dataPoint.getTagValue(Event.LAYER)).intValue()).name());

        return event;
    }

    private void sortEvents(Events events, EventQueryCondition condition) {
        if (events.getEvents().isEmpty()) {
            return;
        }

        final Comparator<org.apache.skywalking.oap.server.core.query.type.event.Event> c =
                buildComparator(isNull(condition.getOrder()) ? Order.DES : condition.getOrder());
        final PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());
        events.setEvents(
                events.getEvents()
                        .stream()
                        .sorted(c)
                        .skip(page.getFrom())
                        .limit(page.getLimit())
                        .collect(Collectors.toList())
        );
    }

    private Comparator<org.apache.skywalking.oap.server.core.query.type.event.Event> buildComparator(Order queryOrder) {
        Comparator<org.apache.skywalking.oap.server.core.query.type.event.Event> c = Comparator.comparingLong(org.apache.skywalking.oap.server.core.query.type.event.Event::getStartTime);
        if (queryOrder == Order.DES) {
            c = c.reversed();
        }
        return c;
    }
}
