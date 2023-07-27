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

package org.apache.skywalking.oap.server.core.alarm.provider.pagerduty;

import java.util.ArrayList;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.Rules;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class PagerDutyHookCallbackTest {

    @Test
    @Disabled
    public void testWithRealAccount() throws Exception {
        // replace this with your actual integration key(s) and run this test manually
        List<String> integrationKeys = Arrays.asList(
                "dummy-integration-key"
        );

        Rules rules = new Rules();
        PagerDutySettings setting1 = new PagerDutySettings("setting1", AlarmHooksType.pagerduty, true);
        setting1.setIntegrationKeys(integrationKeys);
        setting1.setTextTemplate("Apache SkyWalking Alarm: \\n %s.");
        PagerDutySettings setting2 = new PagerDutySettings("setting2", AlarmHooksType.pagerduty, false);
        setting2.setIntegrationKeys(integrationKeys);
        setting2.setTextTemplate("Apache SkyWalking Alarm: \\n %s.");
        rules.getPagerDutySettingsMap().put(setting1.getFormattedName(), setting1);
        rules.getPagerDutySettingsMap().put(setting2.getFormattedName(), setting2);
        PagerDutyHookCallback pagerDutyHookCallback = new PagerDutyHookCallback(
            new AlarmRulesWatcher(rules, null)
        );
        List<AlarmMessage> alarmMessages = new ArrayList<>(2);
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setScopeId(DefaultScopeDefine.SERVICE);
        alarmMessage.setRuleName("service_resp_time_rule");
        alarmMessage.setAlarmMessage("alarmMessage with [DefaultScopeDefine.All]");
        alarmMessage.getHooks().add(setting1.getFormattedName());
        alarmMessages.add(alarmMessage);
        AlarmMessage anotherAlarmMessage = new AlarmMessage();
        anotherAlarmMessage.setRuleName("service_resp_time_rule_2");
        anotherAlarmMessage.setScopeId(DefaultScopeDefine.ENDPOINT);
        anotherAlarmMessage.setAlarmMessage("anotherAlarmMessage with [DefaultScopeDefine.Endpoint]");
        anotherAlarmMessage.getHooks().add(setting2.getFormattedName());
        alarmMessages.add(anotherAlarmMessage);

        pagerDutyHookCallback.doAlarm(alarmMessages);

        // please check your pagerduty account to see if the alarm is sent
    }
}
