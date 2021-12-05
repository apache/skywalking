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
import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.oap.server.core.query.PaginationUtils;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.source.Event;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.util.List;

/**
 * {@link org.apache.skywalking.oap.server.core.source.Event} is a stream
 */
public class BanyanDBEventQueryDAO extends AbstractBanyanDBDAO implements IEventQueryDAO {
    public BanyanDBEventQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public Events queryEvents(EventQueryCondition condition) throws Exception {
        List<org.apache.skywalking.oap.server.core.query.type.event.Event> eventList = query(org.apache.skywalking.oap.server.core.query.type.event.Event.class,
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
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
                });

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

    private void buildConditions(EventQueryCondition condition, final StreamQuery query) {
        if (!Strings.isNullOrEmpty(condition.getUuid())) {
            query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", Event.UUID, condition.getUuid()));
        }
        final Source source = condition.getSource();
        if (source != null) {
            if (!Strings.isNullOrEmpty(source.getService())) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", Event.SERVICE, source.getService()));
            }
            if (!Strings.isNullOrEmpty(source.getServiceInstance())) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", Event.SERVICE_INSTANCE, source.getServiceInstance()));
            }
            if (!Strings.isNullOrEmpty(source.getEndpoint())) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", Event.ENDPOINT, source.getEndpoint()));
            }
        }
        if (!Strings.isNullOrEmpty(condition.getName())) {
            query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", Event.NAME, condition.getName()));
        }
        if (condition.getType() != null) {
            query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", Event.TYPE, condition.getType().name()));
        }
        final Duration time = condition.getTime();
        if (time != null) {
            if (time.getStartTimestamp() > 0) {
                query.appendCondition(PairQueryCondition.LongQueryCondition.gt("searchable", Event.START_TIME, time.getStartTimestamp()));
            }
            if (time.getEndTimestamp() > 0) {
                query.appendCondition(PairQueryCondition.LongQueryCondition.gt("searchable", Event.END_TIME, time.getEndTimestamp()));
            }
        }
    }
}
