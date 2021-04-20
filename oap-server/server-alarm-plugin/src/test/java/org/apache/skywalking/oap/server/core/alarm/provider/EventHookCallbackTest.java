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
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventHookCallbackTest {

    private ModuleManager moduleManager = mock(ModuleManager.class);

    private ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);

    private ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);

    private MockEventAnalyzerService mockEventAnalyzerService = mock(MockEventAnalyzerService.class);

    @Test
    public void testEventCallback() throws Exception {
        List<AlarmMessage> msgs = mockAlarmMessagesHasSingleElement();
        EventHookCallback callback = new EventHookCallback(this.moduleManager);
        when(moduleManager.find("event-analyzer")).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(EventAnalyzerService.class)).thenReturn(mockEventAnalyzerService);

        //No args are judged here, some args are replaced in private methods.
        callback.doAlarm(msgs);
        verify(mockEventAnalyzerService).analyze(any(Event.class));

        EventAnalyzerService eventAnalyzerService = mock(EventAnalyzerServiceImpl.class);
        when(moduleServiceHolder.getService(EventAnalyzerService.class)).thenReturn(eventAnalyzerService);
        callback.doAlarm(msgs);
        verify(mockEventAnalyzerService).analyze(any(Event.class));

        Method method = EventHookCallback.class.getDeclaredMethod("constructCurrentEvent", AlarmMessage.class);
        method.setAccessible(true);
        Object listEvent = method.invoke(callback, msgs.get(0));
        List<Event> events = (List<Event>) listEvent;
        Event event = events.get(0);
        AlarmMessage alarm = msgs.get(0);

        assertEquals(event.getMessage(), alarm.getAlarmMessage());
        assertEquals(event.getSource().getService(), alarm.getName());
        assertEquals(event.getName(), "Alarm");
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

    class MockEventAnalyzerService implements EventAnalyzerService {

        @Override
        public void analyze(Event event) {
            //ignore current mock process.
        }
    }
}
