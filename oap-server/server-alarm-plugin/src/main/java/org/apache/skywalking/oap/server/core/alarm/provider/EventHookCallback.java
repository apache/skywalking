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

import java.io.IOException;
import org.apache.skywalking.apm.network.event.v3.Event;
import org.apache.skywalking.apm.network.event.v3.Source;
import org.apache.skywalking.apm.network.event.v3.Type;
import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * EventCallBack: When an alert is present, an event is generated for each alert message. These events are then sent to the internal event analyzer.
 *
 */
public class EventHookCallback implements AlarmCallback {

    private final ModuleManager manager;
    private MetadataQueryService metadataQueryService;

    private MetadataQueryService getMetadataQueryService() {
        if (metadataQueryService == null) {
            this.metadataQueryService = manager.find(CoreModule.NAME)
                                                     .provider()
                                                     .getService(MetadataQueryService.class);
        }
        return metadataQueryService;
    }

    public EventHookCallback(ModuleManager manager) {
        this.manager = manager;
    }

    @Override
    public void doAlarm(List<AlarmMessage> alarmMessage) throws Exception {
        EventAnalyzerService analyzerService = manager.find(EventAnalyzerModule.NAME).provider().getService(EventAnalyzerService.class);
        for (AlarmMessage a : alarmMessage) {
            for (Event event : constructCurrentEvent(a)) {
                analyzerService.analyze(event);
            }
        }
    }

    private String getLayer(String serviceId) throws IOException {
        return getMetadataQueryService().getService(serviceId).getLayers().iterator().next();
    }

    private List<Event> constructCurrentEvent(AlarmMessage msg) throws IOException {
        List<Event> events = new ArrayList<>(2);
        long now = System.currentTimeMillis();
        Event.Builder builder = Event.newBuilder()
                .setUuid(UUID.randomUUID().toString())
                .setName("Alarm")
                .setStartTime(now - (msg.getPeriod() * 60 * 1000))
                .setMessage(msg.getAlarmMessage())
                .setType(Type.Error)
                .setEndTime(now);
               // .setLayer(Layer.GENERAL.name());
        switch (msg.getScopeId()) {
            case DefaultScopeDefine.SERVICE :
                IDManager.ServiceID.ServiceIDDefinition serviceIdDef = IDManager.ServiceID.analysisId(msg.getId0());
                builder.setSource(
                    Source.newBuilder()
                            .setService(serviceIdDef.getName())
                            .build()
                );
                builder.setLayer(getLayer(msg.getId0()));
                events.add(builder.build());
                break;
            case DefaultScopeDefine.SERVICE_RELATION :
                IDManager.ServiceID.ServiceIDDefinition sourceServiceIdDef = IDManager.ServiceID.analysisId(msg.getId0());
                builder.setSource(
                        Source.newBuilder()
                            .setService(sourceServiceIdDef.getName())
                            .build()
                );
                events.add(builder.build());
                IDManager.ServiceID.ServiceIDDefinition destServiceIdDef = IDManager.ServiceID.analysisId(msg.getId1());
                builder.setSource(
                        Source.newBuilder()
                                .setService(destServiceIdDef.getName())
                                .build()
                ).setUuid(UUID.randomUUID().toString());
                builder.setLayer(getLayer(sourceServiceIdDef.isReal() ? msg.getId0() : msg.getId1()));
                events.add(builder.build());
                break;
            case DefaultScopeDefine.SERVICE_INSTANCE :
                IDManager.ServiceInstanceID.InstanceIDDefinition instanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId0());
                builder.setSource(
                        Source.newBuilder()
                                .setServiceInstance(instanceIdDef.getName())
                                .setService(IDManager.ServiceID.analysisId(instanceIdDef.getServiceId()).getName())
                                .build()
                );
                builder.setLayer(getLayer(instanceIdDef.getServiceId()));
                events.add(builder.build());
                break;
            case DefaultScopeDefine.SERVICE_INSTANCE_RELATION :
                IDManager.ServiceInstanceID.InstanceIDDefinition sourceInstanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId0());
                builder.setSource(
                        Source.newBuilder()
                                .setServiceInstance(sourceInstanceIdDef.getName())
                                .setService(IDManager.ServiceID.analysisId(sourceInstanceIdDef.getServiceId()).getName())
                                .build()
                );
                events.add(builder.build());
                IDManager.ServiceInstanceID.InstanceIDDefinition destInstanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId1());
                builder.setSource(
                        Source.newBuilder()
                                .setServiceInstance(destInstanceIdDef.getName())
                                .setService(IDManager.ServiceID.analysisId(destInstanceIdDef.getServiceId()).getName())
                                .build()
                ).setUuid(UUID.randomUUID().toString());
                IDManager.ServiceID.ServiceIDDefinition srcInstanceServiceIdDef = IDManager.ServiceID.analysisId(sourceInstanceIdDef.getServiceId());
                builder.setLayer(getLayer(srcInstanceServiceIdDef.isReal() ? sourceInstanceIdDef.getServiceId() : destInstanceIdDef.getServiceId()));
                events.add(builder.build());
                break;
            case DefaultScopeDefine.ENDPOINT :
                IDManager.EndpointID.EndpointIDDefinition endpointIDDef = IDManager.EndpointID.analysisId(msg.getId0());
                builder.setSource(
                        Source.newBuilder()
                                .setEndpoint(endpointIDDef.getEndpointName())
                                .setService(IDManager.ServiceID.analysisId(endpointIDDef.getServiceId()).getName())
                                .build()
                );
                builder.setLayer(getLayer(endpointIDDef.getServiceId()));
                events.add(builder.build());
                break;
            case DefaultScopeDefine.ENDPOINT_RELATION :
                IDManager.EndpointID.EndpointIDDefinition sourceEndpointIDDef = IDManager.EndpointID.analysisId(msg.getId0());
                builder.setSource(
                        Source.newBuilder()
                                .setEndpoint(sourceEndpointIDDef.getEndpointName())
                                .setService(IDManager.ServiceID.analysisId(sourceEndpointIDDef.getServiceId()).getName())
                                .build()
                );
                events.add(builder.build());
                IDManager.EndpointID.EndpointIDDefinition destEndpointIDDef = IDManager.EndpointID.analysisId(msg.getId1());
                builder.setSource(
                        Source.newBuilder()
                                .setEndpoint(destEndpointIDDef.getEndpointName())
                                .setService(IDManager.ServiceID.analysisId(destEndpointIDDef.getServiceId()).getName())
                                .build()
                ).setUuid(UUID.randomUUID().toString());
                IDManager.ServiceID.ServiceIDDefinition srcEndpointServiceIdDef = IDManager.ServiceID.analysisId(sourceEndpointIDDef.getServiceId());
                IDManager.ServiceID.ServiceIDDefinition destEndpointServiceIdDef = IDManager.ServiceID.analysisId(destEndpointIDDef.getServiceId());
                builder.setLayer(getLayer(srcEndpointServiceIdDef.isReal() ? sourceEndpointIDDef.getServiceId() : destEndpointIDDef.getServiceId()));
                events.add(builder.build());
                break;
        }
        return events;
    }
}
