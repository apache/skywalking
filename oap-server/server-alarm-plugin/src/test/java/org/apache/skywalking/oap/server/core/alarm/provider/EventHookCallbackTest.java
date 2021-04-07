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

import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.Assert.assertTrue;

/**
 * EventHookCallbackTest.
 *
 */
public class EventHookCallbackTest {

    private AlarmModuleProvider moduleProvider;

    @Before
    public void setUp() throws Exception {
        ServiceLoader<ModuleProvider> serviceLoader = ServiceLoader.load(ModuleProvider.class);
        Iterator<ModuleProvider> providerIterator = serviceLoader.iterator();

        assertTrue(providerIterator.hasNext());

        moduleProvider = (AlarmModuleProvider) providerIterator.next();

        moduleProvider.createConfigBeanIfAbsent();

        moduleProvider.prepare();
    }

    @Test
    public void testEventCallback() {
        List<AlarmMessage> msgs = new ArrayList<>();
        AlarmMessage msg = constructAlarmMessage();
        msgs.add(msg);
        new EventHookCallback(moduleProvider.getModuleManager()).doAlarm(msgs);
    }

    private AlarmMessage constructAlarmMessage() {
        AlarmMessage msg = new AlarmMessage();
        msg.setScopeId(DefaultScopeDefine.SERVICE);
        msg.setScope("SERVICE");
        msg.setName("test-skywalking");
        msg.setId0("dGVzdC1za3l3YWxraW5n.1");
        msg.setAlarmMessage("Alarm caused by Rule service_resp_time_rule");
        msg.setStartTime(System.currentTimeMillis());
        return msg;
    }
}
