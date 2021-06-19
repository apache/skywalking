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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.skywalking.oap.server.core.source.Event;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.ESEventQueryDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;

public class ES7EventQueryDAO extends ESEventQueryDAO {
    public ES7EventQueryDAO(final ElasticSearchClient client) {
        super(client);
    }

    @Override
    public Events queryEvents(final EventQueryCondition condition) throws Exception {
        final SearchSourceBuilder sourceBuilder = buildQuery(condition);
        return getEventsResultByCurrentBuilder(sourceBuilder);
    }

    @Override
    public Events queryEvents(List<EventQueryCondition> conditionList) throws Exception {
        final SearchSourceBuilder sourceBuilder = buildQuery(conditionList);
        return getEventsResultByCurrentBuilder(sourceBuilder);
    }

    private Events getEventsResultByCurrentBuilder(final SearchSourceBuilder sourceBuilder) throws IOException {
        final SearchResponse response = getClient()
                .search(IndexController.LogicIndicesRegister.getPhysicalTableName(Event.INDEX_NAME), sourceBuilder);

        final Events events = new Events();
        events.setTotal(response.getHits().getTotalHits().value);
        events.setEvents(Stream.of(response.getHits().getHits())
                .map(this::parseSearchHit)
                .collect(Collectors.toList()));
        return events;
    }
}
