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
import java.io.Reader;

import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

/**
 * @author kezhenxu94
 */
public class AlarmRulesWatcherTest {
    @Spy
    private AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(new Rules(), null);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldSetAlarmRulesOnEventChanged() throws IOException {
        assertTrue(alarmRulesWatcher.getRules().isEmpty());

        Reader reader = ResourceUtils.read("alarm-settings.yml");
        char[] chars = new char[1024 * 1024];
        int length = reader.read(chars);

        alarmRulesWatcher.notify(new ConfigChangeWatcher.ConfigChangeEvent(new String(chars, 0, length), ConfigChangeWatcher.EventType.MODIFY));

        assertEquals(2, alarmRulesWatcher.getRules().size());
        assertEquals(2, alarmRulesWatcher.getWebHooks().size());
        assertEquals(2, alarmRulesWatcher.getRunningContext().size());
    }

    @Test
    public void shouldClearAlarmRulesOnEventDeleted() throws IOException {
        Reader reader = ResourceUtils.read("alarm-settings.yml");
        Rules defaultRules = new RulesReader(reader).readRules();

        alarmRulesWatcher = spy(new AlarmRulesWatcher(defaultRules, null));

        alarmRulesWatcher.notify(new ConfigChangeWatcher.ConfigChangeEvent("whatever", ConfigChangeWatcher.EventType.DELETE));

        assertEquals(0, alarmRulesWatcher.getRules().size());
        assertEquals(0, alarmRulesWatcher.getWebHooks().size());
        assertEquals(0, alarmRulesWatcher.getRunningContext().size());
    }
}