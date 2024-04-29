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

import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;

public class AlarmRulesWatcherTest {
    @Spy
    private AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(new Rules(), null);

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "service_percent", "testColumn", Column.ValueDataType.COMMON_VALUE, 0, Scope.Service.getScopeId());
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "endpoint_percent", "testColumn", Column.ValueDataType.COMMON_VALUE, 0, Scope.Endpoint.getScopeId());
    }

    @Test
    public void shouldSetAlarmRulesOnEventChanged() throws IOException {
        assertTrue(alarmRulesWatcher.getRules().isEmpty());

        Reader reader = ResourceUtils.read("alarm-settings.yml");
        char[] chars = new char[1024 * 1024];
        int length = reader.read(chars);

        alarmRulesWatcher.notify(new ConfigChangeWatcher.ConfigChangeEvent(new String(chars, 0, length), ConfigChangeWatcher.EventType.MODIFY));

        assertEquals(5, alarmRulesWatcher.getRules().size());
        assertEquals(2, alarmRulesWatcher.getWebHooks().get(AlarmHooksType.webhook.name() + ".default").getUrls().size());
        assertNotNull(alarmRulesWatcher.getGrpchookSetting());
        assertEquals(9888, alarmRulesWatcher.getGrpchookSetting().get(AlarmHooksType.gRPC.name() + ".default").getTargetPort());
        assertEquals(4, alarmRulesWatcher.getRunningContext().size());
        assertNotNull(alarmRulesWatcher.getDingtalkSettings());
        assertNotNull(alarmRulesWatcher.getWechatSettings());
        assertEquals(2, alarmRulesWatcher.getSlackSettings().size());
        assertNotNull(alarmRulesWatcher.getWeLinkSettings());
    }

    @Test
    public void shouldClearAlarmRulesOnEventDeleted() throws IOException {
        Reader reader = ResourceUtils.read("alarm-settings.yml");
        Rules defaultRules = new RulesReader(reader).readRules();

        alarmRulesWatcher = spy(new AlarmRulesWatcher(defaultRules, null));

        alarmRulesWatcher.notify(new ConfigChangeWatcher.ConfigChangeEvent("whatever", ConfigChangeWatcher.EventType.DELETE));

        assertEquals(0, alarmRulesWatcher.getRules().size());
        assertEquals(0, alarmRulesWatcher.getWebHooks().size());
        assertTrue(CollectionUtils.isEmpty(alarmRulesWatcher.getGrpchookSetting()));
        assertEquals(0, alarmRulesWatcher.getRunningContext().size());
    }

    @Test
    public void shouldKeepExistedRunningRuleIfAlarmRuleExists() throws IllegalExpressionException {
        AlarmRule rule = newAlarmRule("name1", "avg(service_percent) < 80");
        Rules rules = new Rules();
        rules.getRules().add(rule);

        alarmRulesWatcher = spy(new AlarmRulesWatcher(rules, null));
        assertEquals(1, alarmRulesWatcher.getRunningContext().size());
        assertEquals(1, alarmRulesWatcher.getRunningContext().get(rule.getExpression()).size());

        RunningRule runningRule = alarmRulesWatcher.getRunningContext().get(rule.getExpression()).get(0);

        Rules updatedRules = new Rules();
        updatedRules.getRules().addAll(Arrays.asList(rule, newAlarmRule("name2", "avg(service_percent) < 80")));

        alarmRulesWatcher.notify(updatedRules);

        assertEquals(1, alarmRulesWatcher.getRunningContext().size());
        assertEquals(2, alarmRulesWatcher.getRunningContext().get(rule.getExpression()).size());
        assertEquals(
                runningRule, alarmRulesWatcher.getRunningContext().get(rule.getExpression()).get(0),
                "The same alarm rule should map to the same existed running rule");
    }

    @Test
    public void shouldRemoveRunningRuleIfAlarmRuleIsRemoved() throws IllegalExpressionException {
        AlarmRule rule = newAlarmRule("name1", "avg(service_percent) < 80");
        Rules rules = new Rules();
        rules.getRules().add(rule);

        alarmRulesWatcher = spy(new AlarmRulesWatcher(rules, null));
        assertEquals(1, alarmRulesWatcher.getRunningContext().size());
        assertEquals(1, alarmRulesWatcher.getRunningContext().get(rule.getExpression()).size());

        RunningRule runningRule = alarmRulesWatcher.getRunningContext().get(rule.getExpression()).get(0);

        Rules updatedRules = new Rules();
        updatedRules.getRules().add(newAlarmRule("name2", "avg(service_percent) < 80"));

        alarmRulesWatcher.notify(updatedRules);

        assertEquals(1, alarmRulesWatcher.getRunningContext().size());
        assertEquals(1, alarmRulesWatcher.getRunningContext().get(rule.getExpression()).size());
        assertNotEquals(
                runningRule, alarmRulesWatcher.getRunningContext().get(rule.getExpression()).get(0),
                "The new alarm rule should map to a different running rule");
    }

    @Test
    public void shouldReplaceRunningRuleIfAlarmRulesAreReplaced() throws IllegalExpressionException {
        AlarmRule rule = newAlarmRule("name1", "avg(service_percent) < 80");
        Rules rules = new Rules();
        rules.getRules().add(rule);

        alarmRulesWatcher = spy(new AlarmRulesWatcher(rules, null));
        assertEquals(1, alarmRulesWatcher.getRunningContext().size());
        assertEquals(1, alarmRulesWatcher.getRunningContext().get(rule.getExpression()).size());

        Rules updatedRules = new Rules();
        // replace the original alarm rules
        updatedRules.getRules()
                    .addAll(Arrays.asList(
                        newAlarmRule("name2", "avg(service_percent) < 90"),
                        newAlarmRule("name3", "avg(service_percent) < 99")
                    ));

        alarmRulesWatcher.notify(updatedRules);

        assertEquals(2, alarmRulesWatcher.getRunningContext().size());
        assertNull(alarmRulesWatcher.getRunningContext().get("avg(service_percent) < 80"));
        assertEquals(1, alarmRulesWatcher.getRunningContext().get("avg(service_percent) < 90").size());
        assertEquals(1, alarmRulesWatcher.getRunningContext().get("avg(service_percent) < 99").size());
    }

    private AlarmRule newAlarmRule(String name, String expression) throws IllegalExpressionException {
       AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName(name);
        alarmRule.setIncludeNames(new ArrayList<String>() {
            {
                add("1");
                add("2");
            }
        });
        alarmRule.setExcludeNames(new ArrayList<String>() {
            {
                add("3");
                add("4");
            }
        });
        alarmRule.setMessage("test");
        alarmRule.setExpression(expression);
        alarmRule.setPeriod(1);
        alarmRule.setSilencePeriod(2);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        return alarmRule;
    }
}
