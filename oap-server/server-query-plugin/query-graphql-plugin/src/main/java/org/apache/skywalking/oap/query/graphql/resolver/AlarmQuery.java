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

package org.apache.skywalking.oap.query.graphql.resolver;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;

import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.AlarmQueryService;
import org.apache.skywalking.oap.server.core.query.EventQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.AlarmTrend;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.event.Event;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.skywalking.oap.server.library.util.CollectionUtils.isNotEmpty;

public class AlarmQuery implements GraphQLQueryResolver {
    private final ModuleManager moduleManager;

    private AlarmQueryService queryService;

    private EventQueryService eventQueryService;

    public AlarmQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private AlarmQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME).provider().getService(AlarmQueryService.class);
        }
        return queryService;
    }

    private EventQueryService getEventQueryService() {
        if (eventQueryService == null) {
            this.eventQueryService = moduleManager.find(CoreModule.NAME).provider().getService(EventQueryService.class);
        }
        return eventQueryService;
    }

    public AlarmTrend getAlarmTrend(final Duration duration) {
        return new AlarmTrend();
    }

    public Alarms getAlarm(final Duration duration, final Scope scope, final String keyword,
                           final Pagination paging, final List<Tag> tags,
                           final DataFetchingEnvironment env) throws Exception {
        Integer scopeId = null;
        if (scope != null) {
            scopeId = scope.getScopeId();
        }
        long startSecondTB = 0;
        long endSecondTB = 0;
        final EventQueryCondition.EventQueryConditionBuilder conditionPrototype =
            EventQueryCondition.builder()
                               .paging(new Pagination(1, IEventQueryDAO.MAX_SIZE, false));
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
            conditionPrototype.time(duration);
        }
        Alarms alarms = getQueryService().getAlarm(
            scopeId, keyword, paging, startSecondTB, endSecondTB, tags);

        final boolean selectEvents = env.getSelectionSet().get().entrySet().stream().anyMatch(it -> it.getKey().contains("/events/"));

        if (selectEvents) {
            return findRelevantEvents(alarms, conditionPrototype);
        }

        return alarms;
    }

    private Alarms findRelevantEvents(
        final Alarms alarms,
        final EventQueryCondition.EventQueryConditionBuilder conditionPrototype
    ) throws Exception {

        if (alarms.getTotal() < 1) {
            return alarms;
        }

        final List<EventQueryCondition> allConditions =
            alarms.getMsgs()
                  .stream()
                  .flatMap(m -> buildEventSources(m).stream().map(conditionPrototype::source))
                  .map(EventQueryCondition.EventQueryConditionBuilder::build)
                  .collect(Collectors.toList());

        final List<Event> events = getEventQueryService().queryEvents(allConditions).getEvents();
        final Map<String, List<Event>> eventsKeyedBySourceId =
            events.stream()
                  .filter(it -> !isNullOrEmpty(buildSourceID(it)))
                  .collect(Collectors.groupingBy(this::buildSourceID));

        alarms.getMsgs().forEach(a -> {
            if (isNotEmpty(eventsKeyedBySourceId.get(a.getId()))) {
                a.getEvents().addAll(eventsKeyedBySourceId.get(a.getId()));
            }
            if (isNotEmpty(eventsKeyedBySourceId.get(a.getId1()))) {
                a.getEvents().addAll(eventsKeyedBySourceId.get(a.getId1()));
            }
        });
        return alarms;
    }

    private List<Source> buildEventSources(AlarmMessage msg) {
        final List<Source> sources = new ArrayList<>(2);
        final Source.SourceBuilder sourcePrototype = Source.builder();
        switch (msg.getScopeId()) {
            case DefaultScopeDefine.SERVICE_RELATION:
                final IDManager.ServiceID.ServiceIDDefinition destServiceIdDef = IDManager.ServiceID.analysisId(msg.getId1());
                sources.add(sourcePrototype.service(destServiceIdDef.getName()).build());
                // fall through
            case DefaultScopeDefine.SERVICE:
                final IDManager.ServiceID.ServiceIDDefinition sourceServiceIdDef = IDManager.ServiceID.analysisId(msg.getId());
                sources.add(sourcePrototype.service(sourceServiceIdDef.getName()).build());
                break;

            case DefaultScopeDefine.SERVICE_INSTANCE_RELATION:
                final IDManager.ServiceInstanceID.InstanceIDDefinition destInstanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId1());
                final String destServiceName = IDManager.ServiceID.analysisId(destInstanceIdDef.getServiceId()).getName();
                sources.add(sourcePrototype.service(destServiceName).serviceInstance(destInstanceIdDef.getName()).build());
                // fall through
            case DefaultScopeDefine.SERVICE_INSTANCE:
                final IDManager.ServiceInstanceID.InstanceIDDefinition sourceInstanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId());
                final String serviceName = IDManager.ServiceID.analysisId(sourceInstanceIdDef.getServiceId()).getName();
                sources.add(sourcePrototype.serviceInstance(sourceInstanceIdDef.getName()).service(serviceName).build());
                break;

            case DefaultScopeDefine.ENDPOINT_RELATION:
                final IDManager.EndpointID.EndpointIDDefinition destEndpointIDDef = IDManager.EndpointID.analysisId(msg.getId1());
                final String destEndpointServiceName = IDManager.ServiceID.analysisId(destEndpointIDDef.getServiceId()).getName();
                sources.add(sourcePrototype.service(destEndpointServiceName).build());
                // fall through
            case DefaultScopeDefine.ENDPOINT:
                final IDManager.EndpointID.EndpointIDDefinition endpointIDDef = IDManager.EndpointID.analysisId(msg.getId());
                final String endpointServiceName = IDManager.ServiceID.analysisId(endpointIDDef.getServiceId()).getName();
                sources.add(sourcePrototype.service(endpointServiceName).build());
                break;
        }

        return sources;
    }

    protected String buildSourceID(final Event event) {
        final Source source = event.getSource();

        if (isNull(source)) {
            return "";
        }

        final String service = source.getService();
        final String serviceId = IDManager.ServiceID.buildId(service, true);
        if (isNullOrEmpty(service)) {
            return "";
        }

        final String instance = source.getServiceInstance();
        if (isNullOrEmpty(instance)) {
            return serviceId;
        }

        return IDManager.ServiceInstanceID.buildId(serviceId, instance);
    }
}
