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
import java.util.UUID;

/**
 * EventCallBack: When an alert is present, an event is generated for each alert message. These events are then sent to the internal event analyzer.
 *
 * @author cchen chenmudu@gmail.com 2021/4/6 1:18
 */
public class EventHookCallback implements AlarmCallback {

    private final ModuleManager manager;

    public EventHookCallback(ModuleManager manager) {
        this.manager = manager;
    }


    @Override
    public void doAlarm(List<AlarmMessage> alarmMessage) {
        if(null == manager) {
            return ;
        }
        EventAnalyzerService analyzerService = manager.find(EventAnalyzerModule.NAME).provider().getService(EventAnalyzerService.class);
        alarmMessage.forEach(a -> {
            analyzerService.analyze(constructCurrentEvent(a));
        });
    }

    private Event constructCurrentEvent(AlarmMessage msg) {
        long millis = System.currentTimeMillis();
        Event event =  Event.newBuilder()
                .setUuid(UUID.randomUUID().toString())
                .setName("Alarm")
                .setStartTime(millis)
                .setMessage(msg.getAlarmMessage())
                .setType(Type.Error)
                .setEndTime(millis)
                .build();

        switch (msg.getScopeId()) {
            case DefaultScopeDefine.SERVICE :
                IDManager.ServiceID.ServiceIDDefinition serviceIdDef = IDManager.ServiceID.analysisId(msg.getId0());
                event = event.toBuilder().setSource(
                    Source.newBuilder()
                            .setService(serviceIdDef.getName())
                            .build()
                ).build();
                break;
            case DefaultScopeDefine.SERVICE_INSTANCE :
                IDManager.ServiceInstanceID.InstanceIDDefinition instanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId0());
                event = event.toBuilder().setSource(
                        Source.newBuilder()
                                .setServiceInstance(instanceIdDef.getName())
                                .build()
                ).build();
                break;
            case DefaultScopeDefine.ENDPOINT :
                IDManager.EndpointID.EndpointIDDefinition endpointIDDef = IDManager.EndpointID.analysisId(msg.getId0());
                event = event.toBuilder().setSource(
                        Source.newBuilder()
                                .setEndpoint(endpointIDDef.getEndpointName())
                                .build()
                ).build();
                break;
            default:
                event = event.toBuilder().setSource(
                        Source.newBuilder()
                                .setService(msg.getName())
                                .build()
                ).build();
                break;
        }
        return event;
    }
}
