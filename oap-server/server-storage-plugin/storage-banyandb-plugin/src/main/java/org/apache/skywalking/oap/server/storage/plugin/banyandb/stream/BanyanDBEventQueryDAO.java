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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.v1.client.AbstractCriteria;
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.Element;
import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.query.PaginationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.analysis.record.Event;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;

public class BanyanDBEventQueryDAO extends AbstractBanyanDBDAO implements IEventQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(
            Event.UUID, Event.SERVICE, Event.SERVICE_INSTANCE, Event.ENDPOINT, Event.NAME,
            Event.MESSAGE, Event.TYPE, Event.START_TIME, Event.END_TIME, Event.PARAMETERS, Event.LAYER, Event.TIMESTAMP);

    public BanyanDBEventQueryDAO(final BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public Events queryEvents(EventQueryCondition condition) throws Exception {
        final Duration time = condition.getTime();
        boolean isColdStage = time != null && time.isColdStage();
        StreamQueryResponse resp = query(isColdStage, Event.INDEX_NAME, TAGS, getTimestampRange(time), buildQuery(Collections.singletonList(condition)));
        Events events = new Events();
        if (resp.size() == 0) {
            return events;
        }
        for (final Element e : resp.getElements()) {
            events.getEvents().add(buildEventView(e));
        }
        return events;
    }

    @Override
    public Events queryEvents(List<EventQueryCondition> conditionList) throws Exception {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(Event.INDEX_NAME, DownSampling.Minute);
        // Duration should be same for all conditions
        final Duration time = conditionList.get(0).getTime();
        boolean isColdStage = time != null && time.isColdStage();
        StreamQueryResponse resp = query(isColdStage, Event.INDEX_NAME, TAGS, getTimestampRange(time), buildQuery(conditionList));
        Events events = new Events();
        if (resp.size() == 0) {
            return events;
        }
        for (final Element e : resp.getElements()) {
            events.getEvents().add(buildEventView(e));
        }
        return events;
    }

    public QueryBuilder<StreamQuery> buildQuery(final List<EventQueryCondition> conditionList) {
        EventQueryCondition condition = conditionList.get(0);
        final Order queryOrder = isNull(condition.getOrder()) ? Order.DES : condition.getOrder();
        final PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());

        return new QueryBuilder<StreamQuery>() {
            @Override
            protected void apply(StreamQuery query) {
                List<AbstractCriteria> eventsQueryConditions = new ArrayList<>(conditionList.size());
                query.setLimit(page.getLimit());
                query.setOffset(page.getFrom());
                if (queryOrder == Order.ASC) {
                    query.setOrderBy(
                        new AbstractQuery.OrderBy(AbstractQuery.Sort.ASC));
                } else {
                    query.setOrderBy(
                        new AbstractQuery.OrderBy(AbstractQuery.Sort.DESC));
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
            final Element e) {
        final org.apache.skywalking.oap.server.core.query.type.event.Event event =
                new org.apache.skywalking.oap.server.core.query.type.event.Event();

        event.setUuid(e.getTagValue(Event.UUID));

        String service = getValueOrDefault(e, Event.SERVICE, "");
        String serviceInstance = getValueOrDefault(e, Event.SERVICE_INSTANCE, "");
        String endpoint = getValueOrDefault(e, Event.ENDPOINT, "");
        event.setSource(new Source(service, serviceInstance, endpoint));

        event.setName(e.getTagValue(Event.NAME));
        event.setType(EventType.parse(e.getTagValue(Event.TYPE)));
        event.setMessage(e.getTagValue(Event.MESSAGE));
        event.setParameters((String) e.getTagValue(Event.PARAMETERS));
        event.setStartTime(e.getTagValue(Event.START_TIME));
        event.setEndTime(e.getTagValue(Event.END_TIME));
        event.setTimestamp(e.getTagValue(Event.TIMESTAMP));
        event.setLayer(Layer.valueOf(((Number) e.getTagValue(Event.LAYER)).intValue()).name());

        return event;
    }

    private <T> T getValueOrDefault(Element e, String tagName, T defaultValue) {
        T v = e.getTagValue(tagName);
        return v == null ? defaultValue : v;
    } 
}
