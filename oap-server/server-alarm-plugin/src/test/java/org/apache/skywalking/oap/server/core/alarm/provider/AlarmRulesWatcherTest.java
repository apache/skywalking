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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

public class AlarmRulesWatcherTest {
    @Spy
    private AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(new Rules(), null);

    private AlarmRule.AlarmRuleBuilder rulePrototypeBuilder = AlarmRule.builder()
                                                                       .alarmRuleName("name1")
                                                                       .count(1)
                                                                       .includeNames(new ArrayList<String>() {
                                                                           {
                                                                               add("1");
                                                                               add("2");
                                                                           }
                                                                       })
                                                                       .excludeNames(new ArrayList<String>() {
                                                                           {
                                                                               add("3");
                                                                               add("4");
                                                                           }
                                                                       })
                                                                       .message("test")
                                                                       .metricsName("metrics1")
                                                                       .op(">")
                                                                       .period(1)
                                                                       .silencePeriod(2)
                                                                       .tags(new HashMap<String, String>() {{
                                                                           put("key", "value");
                                                                       }})
                                                                       .threshold("2");

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

        assertEquals(3, alarmRulesWatcher.getRules().size());
        assertEquals(2, alarmRulesWatcher.getWebHooks().size());
        assertNotNull(alarmRulesWatcher.getGrpchookSetting());
        assertEquals(9888, alarmRulesWatcher.getGrpchookSetting().getTargetPort());
        assertEquals(2, alarmRulesWatcher.getRunningContext().size());
        assertNotNull(alarmRulesWatcher.getDingtalkSettings());
        assertNotNull(alarmRulesWatcher.getWechatSettings());
        assertNotNull(alarmRulesWatcher.getSlackSettings());
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
        assertNull(alarmRulesWatcher.getGrpchookSetting());
        assertEquals(0, alarmRulesWatcher.getRunningContext().size());
    }

    @Test
    public void shouldKeepExistedRunningRuleIfAlarmRuleExists() {
        AlarmRule.AlarmRuleBuilder ruleBuilder = rulePrototypeBuilder;

        AlarmRule rule = ruleBuilder.build();
        Rules rules = new Rules();
        rules.getRules().add(rule);

        alarmRulesWatcher = spy(new AlarmRulesWatcher(rules, null));
        assertEquals(1, alarmRulesWatcher.getRunningContext().size());
        assertEquals(1, alarmRulesWatcher.getRunningContext().get(rule.getMetricsName()).size());

        RunningRule runningRule = alarmRulesWatcher.getRunningContext().get(rule.getMetricsName()).get(0);

        Rules updatedRules = new Rules();
        updatedRules.getRules().addAll(Arrays.asList(rule, ruleBuilder.alarmRuleName("name2").build()));

        alarmRulesWatcher.notify(updatedRules);

        assertEquals(1, alarmRulesWatcher.getRunningContext().size());
        assertEquals(2, alarmRulesWatcher.getRunningContext().get(rule.getMetricsName()).size());
        assertEquals("The same alarm rule should map to the same existed running rule", runningRule, alarmRulesWatcher.getRunningContext()
                                                                                                                      .get(rule
                                                                                                                          .getMetricsName())
                                                                                                                      .get(0));
    }

    @Test
    public void shouldRemoveRunningRuleIfAlarmRuleIsRemoved() {
        AlarmRule.AlarmRuleBuilder ruleBuilder = rulePrototypeBuilder;

        AlarmRule rule = ruleBuilder.build();
        Rules rules = new Rules();
        rules.getRules().add(rule);

        alarmRulesWatcher = spy(new AlarmRulesWatcher(rules, null));
        assertEquals(1, alarmRulesWatcher.getRunningContext().size());
        assertEquals(1, alarmRulesWatcher.getRunningContext().get(rule.getMetricsName()).size());

        RunningRule runningRule = alarmRulesWatcher.getRunningContext().get(rule.getMetricsName()).get(0);

        Rules updatedRules = new Rules();
        updatedRules.getRules().add(ruleBuilder.alarmRuleName("name2").build());

        alarmRulesWatcher.notify(updatedRules);

        assertEquals(1, alarmRulesWatcher.getRunningContext().size());
        assertEquals(1, alarmRulesWatcher.getRunningContext().get(rule.getMetricsName()).size());
        assertNotEquals("The new alarm rule should map to a different running rule", runningRule, alarmRulesWatcher.getRunningContext()
                                                                                                                   .get(rule
                                                                                                                       .getMetricsName())
                                                                                                                   .get(0));
    }

    @Test
    public void shouldReplaceRunningRuleIfAlarmRulesAreReplaced() {
        AlarmRule.AlarmRuleBuilder ruleBuilder = rulePrototypeBuilder;

        AlarmRule rule = ruleBuilder.build();
        Rules rules = new Rules();
        rules.getRules().add(rule);

        alarmRulesWatcher = spy(new AlarmRulesWatcher(rules, null));
        assertEquals(1, alarmRulesWatcher.getRunningContext().size());
        assertEquals(1, alarmRulesWatcher.getRunningContext().get(rule.getMetricsName()).size());

        Rules updatedRules = new Rules();
        // replace the original alarm rules
        updatedRules.getRules()
                    .addAll(Arrays.asList(ruleBuilder.alarmRuleName("name2")
                                                     .metricsName("metrics2")
                                                     .build(), ruleBuilder.alarmRuleName("name3")
                                                                          .metricsName("metrics3")
                                                                          .build()));

        alarmRulesWatcher.notify(updatedRules);

        assertEquals(2, alarmRulesWatcher.getRunningContext().size());
        assertNull(alarmRulesWatcher.getRunningContext().get("metrics1"));
        assertEquals(1, alarmRulesWatcher.getRunningContext().get("metrics2").size());
        assertEquals(1, alarmRulesWatcher.getRunningContext().get("metrics3").size());
    }
}