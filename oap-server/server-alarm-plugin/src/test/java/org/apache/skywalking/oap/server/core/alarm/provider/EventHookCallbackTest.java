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
import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerService;
import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerServiceImpl;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventHookCallbackTest {

    private ModuleManager moduleManager = mock(ModuleManager.class);

    private ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);

    private ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);

    private MockEventAnalyzerService mockEventAnalyzerService = mock(MockEventAnalyzerService.class);

    private EventAnalyzerService eventAnalyzerService = mock(EventAnalyzerServiceImpl.class);

    @Test
    public void testEventCallbackHasRightFlow() throws Exception {
        List<AlarmMessage> msgs = mockAlarmMessagesHasSingleElement();
        EventHookCallback callback = new EventHookCallback(this.moduleManager);
        when(moduleManager.find("event-analyzer")).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(EventAnalyzerService.class)).thenReturn(mockEventAnalyzerService);

        //make sure current service be called.
        callback.doAlarm(msgs);
        verify(mockEventAnalyzerService).analyze(any(Event.class));

        when(moduleServiceHolder.getService(EventAnalyzerService.class)).thenReturn(eventAnalyzerService);
        callback.doAlarm(msgs);
        //Ensure that the current Event is properly constructed
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        verify(eventAnalyzerService).analyze(argument.capture());
        Event value = argument.getValue();
        AlarmMessage msg = msgs.get(0);
        assertEquals(msg.getName(), value.getSource().getService());
        assertEquals("Alarm", value.getName());
        assertEquals(msg.getAlarmMessage(), value.getMessage());
        assertEquals(msg.getPeriod(), (value.getEndTime() - value.getStartTime()) / 1000 / 60);

    }

    @Test
    public void testRelationEventBeProperlyConstructed() {
        List<AlarmMessage> msgs = mockAlarmMessagesHasSourceAndDest();
        EventHookCallback callback = new EventHookCallback(this.moduleManager);
        when(moduleManager.find("event-analyzer")).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(EventAnalyzerService.class)).thenReturn(eventAnalyzerService);
        callback.doAlarm(msgs);

        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        verify(eventAnalyzerService, times(2)).analyze(argument.capture());
        List<Event> events = argument.getAllValues();
        assertEquals(events.size(), 2);
        Event sourceEvent = events.get(0);
        Event destEvent = events.get(1);
        AlarmMessage msg = msgs.get(0);
        assertEquals(sourceEvent.getSource().getService(), IDManager.ServiceID.analysisId(msg.getId0()).getName());
        assertEquals((sourceEvent.getEndTime() - sourceEvent.getStartTime()) / 1000 / 60, msg.getPeriod());
        assertEquals(destEvent.getSource().getService(), IDManager.ServiceID.analysisId(msg.getId1()).getName());
        assertEquals((destEvent.getEndTime() - destEvent.getStartTime()) / 1000 / 60, msg.getPeriod());
    }

    private List<AlarmMessage> mockAlarmMessagesHasSingleElement() {
        AlarmMessage msg = new AlarmMessage();
        msg.setScopeId(DefaultScopeDefine.SERVICE);
        msg.setScope("SERVICE");
        msg.setName("test-skywalking");
        msg.setId0("dGVzdC1za3l3YWxraW5n.1");
        msg.setAlarmMessage("Alarm caused by Rule service_resp_time_rule");
        msg.setPeriod(3);
        return Arrays.asList(msg);
    }

    private List<AlarmMessage> mockAlarmMessagesHasSourceAndDest() {
        AlarmMessage msg = new AlarmMessage();
        msg.setScopeId(DefaultScopeDefine.SERVICE_RELATION);
        msg.setScope("");
        msg.setName("test-skywalking");
        msg.setId0(IDManager.ServiceID.buildId("sourceIdStr", true));
        msg.setId1(IDManager.ServiceID.buildId("destIdStr", true));
        msg.setAlarmMessage("Alarm caused by Rule service_resp_time_rule");
        msg.setPeriod(5);
        return Arrays.asList(msg);
    }

    class MockEventAnalyzerService implements EventAnalyzerService {

        @Override
        public void analyze(Event event) {
            //ignore current mock process.
        }
    }
}
