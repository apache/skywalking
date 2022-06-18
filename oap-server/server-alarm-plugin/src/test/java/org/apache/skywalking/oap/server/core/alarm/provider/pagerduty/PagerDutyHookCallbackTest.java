package org.apache.skywalking.oap.server.core.alarm.provider.pagerduty;

import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.Rules;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PagerDutyHookCallbackTest {

    @Ignore
    @Test
    public void testWithRealAccount() {
        // replace this with your actual integration key and run this test manually
        String yourIntegrationKey = "";

        Rules rules = new Rules();
        rules.setPagerDutySettings(
                PagerDutySettings.builder()
                        .integrationKeys(Arrays.asList(yourIntegrationKey))
                        .textTemplate("Apache SkyWalking Alarm: \n %s.")
                        .build()
        );

        PagerDutyHookCallback pagerDutyHookCallback = new PagerDutyHookCallback(
                new AlarmRulesWatcher(rules, null)
        );

        pagerDutyHookCallback.doAlarm(getMockAlarmMessages());

        // please check your pagerduty account to see if the alarm is sent
    }

    private List<AlarmMessage> getMockAlarmMessages() {
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setScopeId(DefaultScopeDefine.SERVICE);
        alarmMessage.setRuleName("service_resp_time_rule");
        alarmMessage.setAlarmMessage("alarmMessage with [DefaultScopeDefine.All]");

        AlarmMessage anotherAlarmMessage = new AlarmMessage();
        anotherAlarmMessage.setScopeId(DefaultScopeDefine.ENDPOINT);
        anotherAlarmMessage.setRuleName("service_resp_time_rule_2");
        anotherAlarmMessage.setAlarmMessage("anotherAlarmMessage with [DefaultScopeDefine.Endpoint]");

        return Arrays.asList(
                alarmMessage,
                anotherAlarmMessage
        );
    }
}