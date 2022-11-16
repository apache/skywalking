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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
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
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.MatchCNameBuilder;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;

public class ESEventQueryDAO extends EsDAO implements IEventQueryDAO {
    public ESEventQueryDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public Events queryEvents(final EventQueryCondition condition) throws Exception {
        final SearchBuilder sourceBuilder = buildQuery(condition);
        return getEventsResultByCurrentBuilder(sourceBuilder);
    }

    @Override
    public Events queryEvents(List<EventQueryCondition> conditionList) throws Exception {
        final SearchBuilder sourceBuilder = buildQuery(conditionList);
        return getEventsResultByCurrentBuilder(sourceBuilder);
    }

    private Events getEventsResultByCurrentBuilder(final SearchBuilder searchBuilder)
        throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(Event.INDEX_NAME);
        final SearchResponse response = getClient().search(index, searchBuilder.build());
        final Events events = new Events();
        events.setEvents(response.getHits().getHits().stream()
                                 .map(this::parseSearchHit)
                                 .collect(Collectors.toList()));
        return events;
    }

    private void buildMustQueryListByCondition(final EventQueryCondition condition,
                                               final BoolQueryBuilder query) {
        if (IndexController.LogicIndicesRegister.isPhysicalTable(Event.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, Event.INDEX_NAME));
        }
        
        if (!isNullOrEmpty(condition.getUuid())) {
            query.must(Query.term(Event.UUID, condition.getUuid()));
        }

        final Source source = condition.getSource();
        if (source != null) {
            if (!isNullOrEmpty(source.getService())) {
                query.must(Query.term(Event.SERVICE, source.getService()));
            }
            if (!isNullOrEmpty(source.getServiceInstance())) {
                query.must(Query.term(Event.SERVICE_INSTANCE, source.getServiceInstance()));
            }
            if (!isNullOrEmpty(source.getEndpoint())) {
                query.must(Query.matchPhrase(
                    MatchCNameBuilder.INSTANCE.build(Event.ENDPOINT),
                    source.getEndpoint()
                ));
            }
        }

        if (!isNullOrEmpty(condition.getName())) {
            query.must(Query.term(Event.NAME, condition.getName()));
        }

        if (condition.getType() != null) {
            query.must(Query.term(Event.TYPE, condition.getType().name()));
        }

        final Duration startTime = condition.getTime();
        if (startTime != null) {
            if (startTime.getStartTimestamp() > 0) {
                query.must(Query.range(Event.START_TIME).gt(startTime.getStartTimestamp()));
            }
            if (startTime.getEndTimestamp() > 0) {
                query.must(Query.range(Event.END_TIME).lt(startTime.getEndTimestamp()));
            }
        }

        if (!isNullOrEmpty(condition.getLayer())) {
            query.must(Query.term(Event.LAYER, condition.getLayer()));
        }
    }

    protected SearchBuilder buildQuery(final List<EventQueryCondition> conditionList) {
        final BoolQueryBuilder query = Query.bool();

        conditionList.forEach(condition -> {
            final BoolQueryBuilder bool = Query.bool();
            query.should(bool);
            buildMustQueryListByCondition(condition, bool);
        });
        EventQueryCondition condition = conditionList.get(0);
        final Order queryOrder = isNull(condition.getOrder()) ? Order.DES : condition.getOrder();
        final PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());

        return Search.builder().query(query)
                     .sort(
                         Event.START_TIME,
                         Order.DES.equals(queryOrder) ? Sort.Order.DESC : Sort.Order.ASC
                     )
                     .from(page.getFrom())
                     .size(page.getLimit());
    }

    protected SearchBuilder buildQuery(final EventQueryCondition condition) {
        final BoolQueryBuilder query = Query.bool();

        buildMustQueryListByCondition(condition, query);

        final Order queryOrder = isNull(condition.getOrder()) ? Order.DES : condition.getOrder();
        final PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());

        return Search.builder()
                     .query(query)
                     .sort(
                         Event.START_TIME,
                         Order.DES.equals(queryOrder) ? Sort.Order.DESC : Sort.Order.ASC
                     )
                     .from(page.getFrom())
                     .size(page.getLimit());
    }

    protected org.apache.skywalking.oap.server.core.query.type.event.Event parseSearchHit(
        final SearchHit searchHit) {
        final org.apache.skywalking.oap.server.core.query.type.event.Event event =
            new org.apache.skywalking.oap.server.core.query.type.event.Event();

        event.setUuid((String) searchHit.getSource().get(Event.UUID));

        String service = searchHit.getSource().getOrDefault(Event.SERVICE, "").toString();
        String serviceInstance =
            searchHit.getSource().getOrDefault(Event.SERVICE_INSTANCE, "").toString();
        String endpoint = searchHit.getSource().getOrDefault(Event.ENDPOINT, "").toString();
        event.setSource(new Source(service, serviceInstance, endpoint));

        event.setName((String) searchHit.getSource().get(Event.NAME));
        event.setType(EventType.parse(searchHit.getSource().get(Event.TYPE).toString()));
        event.setMessage((String) searchHit.getSource().get(Event.MESSAGE));
        event.setParameters((String) searchHit.getSource().get(Event.PARAMETERS));
        event.setStartTime(Long.parseLong(searchHit.getSource().get(Event.START_TIME).toString()));
        String endTimeStr = searchHit.getSource().getOrDefault(Event.END_TIME, "0").toString();
        if (!endTimeStr.isEmpty() && !Objects.equals(endTimeStr, "0")) {
            event.setEndTime(Long.parseLong(endTimeStr));
        }

        event.setLayer(Layer.valueOf(Integer.parseInt(searchHit.getSource().get(Event.LAYER).toString())).name());

        return event;
    }
}
