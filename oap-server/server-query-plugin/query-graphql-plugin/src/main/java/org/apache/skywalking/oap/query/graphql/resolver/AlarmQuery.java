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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ForkJoinPool;
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
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import static java.util.Objects.nonNull;

public class AlarmQuery implements GraphQLQueryResolver {
    private final ModuleManager moduleManager;
    private AlarmQueryService queryService;
    private EventQueryService eventQueryService;
    private ForkJoinPool forkJoinPool;

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

    private ForkJoinPool getForkJoinPool() {
        if (forkJoinPool == null) {
            this.forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        }
        return forkJoinPool;
    }

    public AlarmTrend getAlarmTrend(final Duration duration) {
        return new AlarmTrend();
    }

    public Alarms getAlarm(final Duration duration, final Scope scope, final String keyword,
                           final Pagination paging, final List<Tag> tags) throws Throwable {
        Integer scopeId = null;
        if (scope != null) {
            scopeId = scope.getScopeId();
        }
        long startSecondTB = 0;
        long endSecondTB = 0;
        EventQueryCondition condition = new EventQueryCondition();
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
            condition.setTime(duration);
        }
        Alarms alarms = getQueryService().getAlarm(
                scopeId, keyword, paging, startSecondTB, endSecondTB, tags);
        return includeEvents2AlarmsByCondition(alarms, condition);
    }

    private Alarms includeEvents2AlarmsByCondition(Alarms alarms, EventQueryCondition condition) throws Exception {
        if (alarms.getTotal() < 1) {
            return alarms;
        }

        final List<EventQueryCondition> allConditions = new ArrayList<>(alarms.getTotal());
        alarms.getMsgs().stream().forEach(m -> {
            constructCurrentSource(m).stream().forEach(c -> {
                final EventQueryCondition currentCondition = constructNewEventQueryCondition(condition);
                currentCondition.setSource(c);
                allConditions.add(currentCondition);
            });
        });

        List<Event> events = getEventQueryService().queryEvents(allConditions).getEvents();
        Map<String, List<Event>> mappingEvents = events.stream().collect(Collectors.toMap(Event::getSourcesString, e -> {
            final List<Event> allEvents = new ArrayList<>();
            allEvents.add(e);
            return allEvents;
        }, (List<Event> firstEvents, List<Event> secondEvents) -> {
                if (CollectionUtils.isNotEmpty(firstEvents)) {
                    firstEvents.addAll(secondEvents);
                }
                return firstEvents;
        }));
        alarms.getMsgs().stream().forEach(a -> {
            if (CollectionUtils.isNotEmpty(mappingEvents.get(a.getId0SourcesStr()))) {
                a.getEvents().addAll(mappingEvents.get(a.getId0SourcesStr()));
            }
            if (Boolean.TRUE.equals(a.getId0LinkId1Flag()) && CollectionUtils.isNotEmpty(mappingEvents.get(a.getId1SourcesStr()))) {
                a.getEvents().addAll(mappingEvents.get(a.getId0SourcesStr()));
            }
        });
        return alarms;
    }

    private List<Source> constructCurrentSource(AlarmMessage msg) {
        List<Source> sources = new ArrayList<>(2);
        switch (msg.getScopeId()) {
            case DefaultScopeDefine.SERVICE :
                IDManager.ServiceID.ServiceIDDefinition serviceIdDef = IDManager.ServiceID.analysisId(msg.getId());
                Source serviceSource = new Source();
                serviceSource.setService(serviceIdDef.getName());
                msg.setId0SourcesStr(serviceIdDef.getName());
                sources.add(serviceSource);
                break;
            case DefaultScopeDefine.SERVICE_RELATION :
                IDManager.ServiceID.ServiceIDDefinition sourceServiceIdDef = IDManager.ServiceID.analysisId(msg.getId());
                Source sourceSource = new Source();
                sourceSource.setService(sourceServiceIdDef.getName());
                msg.setId0SourcesStr(sourceServiceIdDef.getName());
                sources.add(sourceSource);

                IDManager.ServiceID.ServiceIDDefinition destServiceIdDef = IDManager.ServiceID.analysisId(msg.getId1());
                Source destSource = new Source();
                sourceSource.setService(destServiceIdDef.getName());
                msg.setId1SourcesStr(destServiceIdDef.getName());
                msg.setId0LinkId1Flag(true);
                sources.add(destSource);
                break;
            case DefaultScopeDefine.SERVICE_INSTANCE :
                IDManager.ServiceInstanceID.InstanceIDDefinition instanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId());
                Source serviceInstanceSource = new Source();
                serviceInstanceSource.setServiceInstance(instanceIdDef.getName());
                final String serviceName = IDManager.ServiceID.analysisId(instanceIdDef.getServiceId()).getName();
                serviceInstanceSource.setService(serviceName);
                msg.setId0SourcesStr(serviceName + instanceIdDef.getName());
                sources.add(serviceInstanceSource);
                break;
            case DefaultScopeDefine.SERVICE_INSTANCE_RELATION :
                IDManager.ServiceInstanceID.InstanceIDDefinition sourceInstanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId());
                Source sourceServiceInstanceSource = new Source();
                sourceServiceInstanceSource.setServiceInstance(sourceInstanceIdDef.getName());
                final String sourceServiceName = IDManager.ServiceID.analysisId(sourceInstanceIdDef.getServiceId()).getName();
                sourceServiceInstanceSource.setService(sourceServiceName);
                msg.setId0SourcesStr(sourceServiceName + sourceInstanceIdDef.getName());
                sources.add(sourceServiceInstanceSource);

                IDManager.ServiceInstanceID.InstanceIDDefinition destInstanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId1());
                Source destServiceInstanceSource = new Source();
                destServiceInstanceSource.setServiceInstance(destInstanceIdDef.getName());
                final String destServiceName = IDManager.ServiceID.analysisId(destInstanceIdDef.getServiceId()).getName();
                destServiceInstanceSource.setService(destServiceName);
                msg.setId1SourcesStr(destServiceName + destInstanceIdDef.getName());
                msg.setId0LinkId1Flag(true);
                sources.add(destServiceInstanceSource);
                break;
            case DefaultScopeDefine.ENDPOINT :
                IDManager.EndpointID.EndpointIDDefinition endpointIDDef = IDManager.EndpointID.analysisId(msg.getId());
                Source endpointSource = new Source();
                final String endpointServiceName = IDManager.ServiceID.analysisId(endpointIDDef.getServiceId()).getName();
                endpointSource.setService(endpointServiceName);
                msg.setId0SourcesStr(endpointServiceName);
                sources.add(endpointSource);
                break;
            case DefaultScopeDefine.ENDPOINT_RELATION :
                IDManager.EndpointID.EndpointIDDefinition sourceEndpointIDDef = IDManager.EndpointID.analysisId(msg.getId());
                Source sourceEndpointSource = new Source();
                final String sourceEndpointServiceName = IDManager.ServiceID.analysisId(sourceEndpointIDDef.getServiceId()).getName();
                sourceEndpointSource.setService(sourceEndpointServiceName);
                msg.setId0SourcesStr(sourceEndpointServiceName);
                sources.add(sourceEndpointSource);

                IDManager.EndpointID.EndpointIDDefinition destEndpointIDDef = IDManager.EndpointID.analysisId(msg.getId1());
                Source destEndpointSource = new Source();
                final String destEndpointServiceName = IDManager.ServiceID.analysisId(destEndpointIDDef.getServiceId()).getName();
                destEndpointSource.setService(destEndpointServiceName);
                msg.setId1SourcesStr(destEndpointServiceName);
                msg.setId0LinkId1Flag(true);
                sources.add(destEndpointSource);
                break;
        }
        return sources;
    }

    private EventQueryCondition constructNewEventQueryCondition(EventQueryCondition oldCondition) {
        EventQueryCondition newCondition = new EventQueryCondition();
        newCondition.setUuid(oldCondition.getUuid());
        newCondition.setSource(oldCondition.getSource());
        newCondition.setName(oldCondition.getName());
        newCondition.setType(oldCondition.getType());
        newCondition.setTime(oldCondition.getTime());
        newCondition.setOrder(oldCondition.getOrder());
        newCondition.setSize(oldCondition.getSize());
        return newCondition;
    }
}
