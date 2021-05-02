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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.AlarmQueryService;
import org.apache.skywalking.oap.server.core.query.EventQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.AlarmTrend;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.event.Event;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.nonNull;

public class AlarmQuery implements GraphQLQueryResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmQuery.class);

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
                           final Pagination paging, final List<Tag> tags) throws IOException {
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
        Events events = null;
        try {
            events = getEventQueryService().queryEvents(condition);
        } catch (Throwable e) {
            LOGGER.error(e.getMessage(), e);
            return alarms;
        }
        return includeEvents2Alarms(alarms, events);
    }

    private Alarms includeEvents2Alarms(Alarms alarms, Events events) {
        if (alarms.getTotal() < 1 || events.getTotal() < 1) {
            return alarms;
        }
        Map<String, List<Event>> mappingMap = events.getEvents().stream().collect(Collectors.groupingBy(Event::getServiceInSource));
        alarms.getMsgs().forEach(a -> {
            switch (a.getScopeId()) {
                case DefaultScopeDefine.SERVICE :
                    List<Event> serviceEvent = mappingMap.get(IDManager.ServiceID.analysisId(a.getId()).getName());
                    if (CollectionUtils.isNotEmpty(serviceEvent)) {
                        a.setEvents(serviceEvent);
                    }
                    break;
                case DefaultScopeDefine.SERVICE_RELATION :
                    List<Event> sourceServiceEvent = mappingMap.get(IDManager.ServiceID.analysisId(a.getId()));
                    List<Event> destServiceEvent = mappingMap.get(IDManager.ServiceID.analysisId(a.getId1()));
                    if (CollectionUtils.isNotEmpty(sourceServiceEvent)) {
                        a.setEvents(sourceServiceEvent);
                    }
                    if (CollectionUtils.isNotEmpty(destServiceEvent)) {
                        a.getEvents().addAll(destServiceEvent);
                    }
                    break;
                case DefaultScopeDefine.SERVICE_INSTANCE :
                    IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID.analysisId(a.getId());
                    String serviceInstanceName = instanceIDDefinition.getName();
                    String serviceName = IDManager.ServiceID.analysisId(instanceIDDefinition.getServiceId()).getName();
                    List<Event> serviceInstanceEvent = mappingMap.get(serviceName);
                    if (CollectionUtils.isNotEmpty(serviceInstanceEvent)) {
                        List<Event> filterEvents = serviceInstanceEvent.stream().filter(e -> StringUtils.equals(e.getSource().getServiceInstance(), serviceInstanceName)).collect(Collectors.toList());
                        a.setEvents(filterEvents);
                    }
                    break;
                case DefaultScopeDefine.SERVICE_INSTANCE_RELATION :
                    IDManager.ServiceInstanceID.InstanceIDDefinition sourceInstanceIDDefinition = IDManager.ServiceInstanceID.analysisId(a.getId());
                    String sourceServiceInstanceName = sourceInstanceIDDefinition.getName();
                    String sourceServiceName = IDManager.ServiceID.analysisId(sourceInstanceIDDefinition.getServiceId()).getName();

                    IDManager.ServiceInstanceID.InstanceIDDefinition destInstanceIDDefinition = IDManager.ServiceInstanceID.analysisId(a.getId1());
                    String destServiceInstanceName = destInstanceIDDefinition.getName();
                    String destServiceName = IDManager.ServiceID.analysisId(destInstanceIDDefinition.getServiceId()).getName();

                    List<Event> sourceInstanceEvent = mappingMap.get(sourceServiceName);
                    List<Event> destInstanceEvent = mappingMap.get(destServiceName);

                    if (CollectionUtils.isNotEmpty(sourceInstanceEvent)) {
                        List<Event> filterEvents = sourceInstanceEvent.stream().filter(e -> StringUtils.equals(e.getSource().getServiceInstance(), sourceServiceInstanceName)).collect(Collectors.toList());
                        a.setEvents(filterEvents);
                    }
                    if (CollectionUtils.isNotEmpty(destInstanceEvent)) {
                        List<Event> filterEvents = destInstanceEvent.stream().filter(e -> StringUtils.equals(e.getSource().getServiceInstance(), destServiceInstanceName)).collect(Collectors.toList());
                        a.getEvents().addAll(filterEvents);
                    }
                    break;
                case DefaultScopeDefine.ENDPOINT :
                    IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition = IDManager.EndpointID.analysisId(a.getId());
                    String endpointName = endpointIDDefinition.getEndpointName();
                    String endpointServiceName = IDManager.ServiceID.analysisId(endpointIDDefinition.getServiceId()).getName();
                    List<Event> serviceEndpointEvent = mappingMap.get(endpointServiceName);
                    if (CollectionUtils.isNotEmpty(serviceEndpointEvent)) {
                        List<Event> filterEvents = serviceEndpointEvent.stream().filter(e -> StringUtils.equals(e.getSource().getEndpoint(), endpointName)).collect(Collectors.toList());
                        a.setEvents(filterEvents);
                    }
                    break;
                case DefaultScopeDefine.ENDPOINT_RELATION :
                    IDManager.EndpointID.EndpointIDDefinition sourceEndpointIDDefinition = IDManager.EndpointID.analysisId(a.getId());
                    String sourceEndpointName = sourceEndpointIDDefinition.getEndpointName();
                    String sourceEndpointServiceName = IDManager.ServiceID.analysisId(sourceEndpointIDDefinition.getServiceId()).getName();

                    IDManager.EndpointID.EndpointIDDefinition destEndpointIDDefinition = IDManager.EndpointID.analysisId(a.getId1());
                    String destEndpointName = destEndpointIDDefinition.getEndpointName();
                    String destEndpointServiceName = IDManager.ServiceID.analysisId(destEndpointIDDefinition.getServiceId()).getName();

                    List<Event> sourceEndpointEvent = mappingMap.get(sourceEndpointServiceName);
                    List<Event> destEndpointEvent = mappingMap.get(destEndpointServiceName);

                    if (CollectionUtils.isNotEmpty(sourceEndpointEvent)) {
                        List<Event> filterEvents = sourceEndpointEvent.stream().filter(e -> StringUtils.equals(e.getSource().getEndpoint(), sourceEndpointName)).collect(Collectors.toList());
                        a.setEvents(filterEvents);
                    }
                    if (CollectionUtils.isNotEmpty(destEndpointEvent)) {
                        List<Event> filterEvents = destEndpointEvent.stream().filter(e -> StringUtils.equals(e.getSource().getEndpoint(), destEndpointName)).collect(Collectors.toList());
                        a.getEvents().addAll(filterEvents);
                    }
                    break;
            }
        });
        return alarms;
    }
}
