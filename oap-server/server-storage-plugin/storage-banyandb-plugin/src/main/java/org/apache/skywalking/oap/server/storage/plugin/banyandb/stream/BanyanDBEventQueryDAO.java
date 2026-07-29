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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.library.banyandb.v1.client.Element;
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
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
        return doQuery(Collections.singletonList(condition));
    }

    @Override
    public Events queryEvents(List<EventQueryCondition> conditionList) throws Exception {
        return doQuery(conditionList);
    }

    private Events doQuery(final List<EventQueryCondition> conditionList) throws IOException {
        // Duration should be same for all conditions
        final EventQueryCondition first = conditionList.get(0);
        final Duration time = first.getTime();
        final boolean isColdStage = time != null && time.isColdStage();
        final Order queryOrder = isNull(first.getOrder()) ? Order.DES : first.getOrder();
        final PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(first.getPaging());

        final List<Conditions> groups = new ArrayList<>(conditionList.size());
        for (final EventQueryCondition condition : conditionList) {
            final Conditions group = Conditions.group();
            if (!isNullOrEmpty(condition.getUuid())) {
                group.eq(Event.UUID, condition.getUuid());
            }
            final Source source = condition.getSource();
            if (source != null) {
                if (!isNullOrEmpty(source.getService())) {
                    group.eq(Event.SERVICE, source.getService());
                }
                if (!isNullOrEmpty(source.getServiceInstance())) {
                    group.eq(Event.SERVICE_INSTANCE, source.getServiceInstance());
                }
                if (!isNullOrEmpty(source.getEndpoint())) {
                    group.eq(Event.ENDPOINT, source.getEndpoint());
                }
            }
            if (!isNullOrEmpty(condition.getName())) {
                group.eq(Event.NAME, condition.getName());
            }
            if (condition.getType() != null) {
                group.eq(Event.TYPE, condition.getType().name());
            }
            if (!isNullOrEmpty(condition.getLayer())) {
                group.eq(Event.LAYER, Layer.valueOf(condition.getLayer()).value());
            }
            groups.add(group);
        }
        final Conditions where = Conditions.create().or(groups);
        if (queryOrder == Order.ASC) {
            where.orderByAsc();
        } else {
            where.orderByDesc();
        }
        where.limit(page.getLimit()).offset(page.getFrom());

        final StreamQueryResponse resp = queryDebuggable(isColdStage, Event.INDEX_NAME, TAGS,
                getTimestampRange(time), where);
        final Events events = new Events();
        if (resp.size() == 0) {
            return events;
        }
        for (final Element e : resp.getElements()) {
            events.getEvents().add(buildEventView(e));
        }
        return events;
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
