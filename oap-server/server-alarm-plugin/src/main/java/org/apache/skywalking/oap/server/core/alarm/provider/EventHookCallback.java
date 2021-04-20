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

package org.apache.skywalking.oap.server.core.alarm.provider;

import org.apache.skywalking.apm.network.event.v3.Event;
import org.apache.skywalking.apm.network.event.v3.Source;
import org.apache.skywalking.apm.network.event.v3.Type;
import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerService;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * EventCallBack: When an alert is present, an event is generated for each alert message. These events are then sent to the internal event analyzer.
 *
 */
public class EventHookCallback implements AlarmCallback {

    private final ModuleManager manager;

    public EventHookCallback(ModuleManager manager) {
        this.manager = manager;
    }

    @Override
    public void doAlarm(List<AlarmMessage> alarmMessage) {
        if (Objects.isNull(this.manager)) {
            return ;
        }
        EventAnalyzerService analyzerService = manager.find(EventAnalyzerModule.NAME).provider().getService(EventAnalyzerService.class);
        alarmMessage.forEach(a -> {
            for (Event event : constructCurrentEvent(a)) {
                if (Objects.nonNull(event)) {
                    analyzerService.analyze(event);
                }
            }
        });
    }

    private Event[] constructCurrentEvent(AlarmMessage msg) {
        Event[] events = new Event[2];
        long now = System.currentTimeMillis();
        Event.Builder builder = Event.newBuilder()
                .setUuid(UUID.randomUUID().toString())
                .setName("Alarm")
                .setStartTime(now - (msg.getPeriod() * 60 * 1000))
                .setMessage(msg.getAlarmMessage())
                .setType(Type.Error)
                .setEndTime(now);
        switch (msg.getScopeId()) {
            case DefaultScopeDefine.SERVICE :
                IDManager.ServiceID.ServiceIDDefinition singleServiceIdDef = IDManager.ServiceID.analysisId(msg.getId0());
                builder.setSource(
                    Source.newBuilder()
                            .setService(singleServiceIdDef.getName())
                            .build()
                );
                events[0] = builder.build();
                break;
            case DefaultScopeDefine.SERVICE_RELATION :
                IDManager.ServiceID.ServiceIDDefinition doubleServiceIdDef = IDManager.ServiceID.analysisId(msg.getId0());
                builder.setSource(
                        Source.newBuilder()
                            .setService(doubleServiceIdDef.getName())
                            .build()
                );
                events[0] = builder.build();
                doubleServiceIdDef = IDManager.ServiceID.analysisId(msg.getId1());
                builder.setSource(
                        Source.newBuilder()
                                .setService(doubleServiceIdDef.getName())
                                .build()
                ).setUuid(UUID.randomUUID().toString());
                events[1] = builder.build();
                break;
            case DefaultScopeDefine.SERVICE_INSTANCE :
                IDManager.ServiceInstanceID.InstanceIDDefinition singleInstanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId0());
                builder.setSource(
                        Source.newBuilder()
                                .setServiceInstance(singleInstanceIdDef.getName())
                                .setService(IDManager.ServiceID.analysisId(singleInstanceIdDef.getServiceId()).getName())
                                .build()
                );
                events[0] = builder.build();
                break;
            case DefaultScopeDefine.SERVICE_INSTANCE_RELATION :
                IDManager.ServiceInstanceID.InstanceIDDefinition doubleInstanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId0());
                builder.setSource(
                        Source.newBuilder()
                                .setServiceInstance(doubleInstanceIdDef.getName())
                                .setService(IDManager.ServiceID.analysisId(doubleInstanceIdDef.getServiceId()).getName())
                                .build()
                );
                events[0] = builder.build();
                doubleInstanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId1());
                builder.setSource(
                        Source.newBuilder()
                                .setServiceInstance(doubleInstanceIdDef.getName())
                                .setService(IDManager.ServiceID.analysisId(doubleInstanceIdDef.getServiceId()).getName())
                                .build()
                ).setUuid(UUID.randomUUID().toString());
                events[1] = builder.build();
                break;
            case DefaultScopeDefine.ENDPOINT :
                IDManager.EndpointID.EndpointIDDefinition singleEndpointIDDef = IDManager.EndpointID.analysisId(msg.getId0());
                builder.setSource(
                        Source.newBuilder()
                                .setEndpoint(singleEndpointIDDef.getEndpointName())
                                .setService(IDManager.ServiceID.analysisId(singleEndpointIDDef.getServiceId()).getName())
                                .build()
                );
                events[0] = builder.build();
                break;
            case DefaultScopeDefine.ENDPOINT_RELATION :
                IDManager.EndpointID.EndpointIDDefinition doubleEndpointIDDef = IDManager.EndpointID.analysisId(msg.getId0());
                builder.setSource(
                        Source.newBuilder()
                                .setEndpoint(doubleEndpointIDDef.getEndpointName())
                                .setService(IDManager.ServiceID.analysisId(doubleEndpointIDDef.getServiceId()).getName())
                                .build()
                );
                events[0] = builder.build();
                doubleEndpointIDDef = IDManager.EndpointID.analysisId(msg.getId1());
                builder.setSource(
                        Source.newBuilder()
                                .setEndpoint(doubleEndpointIDDef.getEndpointName())
                                .setService(IDManager.ServiceID.analysisId(doubleEndpointIDDef.getServiceId()).getName())
                                .build()
                ).setUuid(UUID.randomUUID().toString());
                events[1] = builder.build();
                break;
        }
        return events;
    }
}
