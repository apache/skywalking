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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.v1.client.AbstractCriteria;
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
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
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
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
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(Event.INDEX_NAME, DownSampling.Minute);
        MeasureQueryResponse resp = query(schema, TAGS,
                Collections.emptySet(), buildQuery(Collections.singletonList(condition)));
        Events events = new Events();
        if (resp.size() == 0) {
            return events;
        }
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            events.getEvents().add(buildEventView(dataPoint));
        }
        return events;
    }

    @Override
    public Events queryEvents(List<EventQueryCondition> conditionList) throws Exception {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(Event.INDEX_NAME, DownSampling.Minute);
        MeasureQueryResponse resp = query(schema, TAGS,
                Collections.emptySet(), buildQuery(conditionList));
        Events events = new Events();
        if (resp.size() == 0) {
            return events;
        }
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            events.getEvents().add(buildEventView(dataPoint));
        }
        return events;
    }

    public QueryBuilder<MeasureQuery> buildQuery(final List<EventQueryCondition> conditionList) {
        EventQueryCondition condition = conditionList.get(0);
        final Order queryOrder = isNull(condition.getOrder()) ? Order.DES : condition.getOrder();
        final PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());

        return new QueryBuilder<MeasureQuery>() {
            @Override
            protected void apply(MeasureQuery query) {
                List<AbstractCriteria> eventsQueryConditions = new ArrayList<>(conditionList.size());
                query.limit(page.getLimit());
                query.offset(page.getFrom());
                if (queryOrder == Order.ASC) {
                    query.setOrderBy(new AbstractQuery.OrderBy(Event.START_TIME, AbstractQuery.Sort.ASC));
                } else {
                    query.setOrderBy(new AbstractQuery.OrderBy(Event.START_TIME, AbstractQuery.Sort.DESC));
                }
                for (final EventQueryCondition condition : conditionList) {
                    List<PairQueryCondition<?>> queryConditions = new ArrayList<>();
                    if (!isNullOrEmpty(condition.getUuid())) {
                        queryConditions.add(eq(Event.UUID, condition.getUuid()));
                    }
                    final Source source = condition.getSource();
                    if (source != null) {
                        if (!isNullOrEmpty(source.getService())) {
                            queryConditions.add(eq(Event.SERVICE, source.getService()));
                        }
                        if (!isNullOrEmpty(source.getServiceInstance())) {
                            queryConditions.add(eq(Event.SERVICE_INSTANCE, source.getServiceInstance()));
                        }
                        if (!isNullOrEmpty(source.getEndpoint())) {
                            queryConditions.add(eq(Event.ENDPOINT, source.getEndpoint()));
                        }
                    }

                    if (!isNullOrEmpty(condition.getName())) {
                        queryConditions.add(eq(Event.NAME, condition.getName()));
                    }

                    if (condition.getType() != null) {
                        queryConditions.add(eq(Event.TYPE, condition.getType().name()));
                    }

                    final Duration startTime = condition.getTime();
                    if (startTime != null) {
                        if (startTime.getStartTimestamp() > 0) {
                            queryConditions.add(gte(Event.START_TIME, startTime.getStartTimestamp()));
                        }
                        if (startTime.getEndTimestamp() > 0) {
                            queryConditions.add(lte(Event.END_TIME, startTime.getEndTimestamp()));
                        }
                    }

                    if (!isNullOrEmpty(condition.getLayer())) {
                        queryConditions.add(eq(Event.LAYER, Layer.valueOf(condition.getLayer()).value()));
                    }
                    eventsQueryConditions.add(and(queryConditions));
                }
                if (eventsQueryConditions.size() == 1) {
                    query.criteria(eventsQueryConditions.get(0));
                } else if (eventsQueryConditions.size() > 1) {
                    query.criteria(or(eventsQueryConditions));
                }
            }
        };
    }

    protected org.apache.skywalking.oap.server.core.query.type.event.Event buildEventView(
            final DataPoint dataPoint) {
        final org.apache.skywalking.oap.server.core.query.type.event.Event event =
                new org.apache.skywalking.oap.server.core.query.type.event.Event();

        event.setUuid(dataPoint.getTagValue(Event.UUID));

        String service = getValueOrDefault(dataPoint, Event.SERVICE, "");
        String serviceInstance = getValueOrDefault(dataPoint, Event.SERVICE_INSTANCE, "");
        String endpoint = getValueOrDefault(dataPoint, Event.ENDPOINT, "");
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

    private <T> T getValueOrDefault(DataPoint dataPoint, String tagName, T defaultValue) {
        T v = dataPoint.getTagValue(tagName);
        return v == null ? defaultValue : v;
    } 
}
