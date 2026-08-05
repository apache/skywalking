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
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PagerDutyHookCallbackTest {

    private static final List<JsonObject> RECEIVED = new CopyOnWriteArrayList<>();

    @RegisterExtension
    public static final ServerExtension SERVER = new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
            sb.service("/v2/enqueue", (ctx, req) -> HttpResponse.from(
                req.aggregate().thenApply(r -> {
                    RECEIVED.add(new Gson().fromJson(r.contentUtf8(), JsonObject.class));
                    return HttpResponse.of(HttpStatus.OK);
                })
            ));
        }
    };

    /**
     * The endpoint shipped as {@code http://} from 9.2.0 until this guard was added, putting the integration key —
     * which {@code getMessageBody} places in the request body — in the clear on every alarm.
     */
    @Test
    public void defaultEndpointMustUseTls() {
        Assertions.assertTrue(
            PagerDutySettings.DEFAULT_EVENTS_API_URL.startsWith("https://"),
            "PagerDuty endpoint must use https, the routing key is sent in the request body, but was: "
                + PagerDutySettings.DEFAULT_EVENTS_API_URL
        );
        PagerDutySettings settings = new PagerDutySettings("s", AlarmHooksType.pagerduty, true);
        Assertions.assertEquals(
            PagerDutySettings.DEFAULT_EVENTS_API_URL, settings.getEventsApiUrl(),
            "a hook that does not configure events-api-url must fall back to the TLS default"
        );
    }

    @Test
    public void testDoAlarmPostsToConfiguredEndpoint() throws Exception {
        RECEIVED.clear();
        Rules rules = new Rules();
        PagerDutySettings setting = new PagerDutySettings("setting1", AlarmHooksType.pagerduty, true);
        setting.setEventsApiUrl("http://127.0.0.1:" + SERVER.httpPort() + "/v2/enqueue");
        setting.setIntegrationKeys(Arrays.asList("key-1", "key-2"));
        setting.setTextTemplate("Apache SkyWalking Alarm: \\n %s.");
        rules.getPagerDutySettingsMap().put(setting.getFormattedName(), setting);

        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setScopeId(DefaultScopeDefine.SERVICE);
        alarmMessage.setRuleName("service_resp_time_rule");
        alarmMessage.setAlarmMessage("alarmMessage with [DefaultScopeDefine.All]");
        alarmMessage.getHooks().add(setting.getFormattedName());

        new PagerDutyHookCallback(new AlarmRulesWatcher(rules, null, null))
            .doAlarm(new ArrayList<>(List.of(alarmMessage)));

        // one request per integration key
        Assertions.assertEquals(2, RECEIVED.size());
        List<String> routingKeys = new ArrayList<>();
        for (JsonObject body : RECEIVED) {
            routingKeys.add(body.get("routing_key").getAsString());
            Assertions.assertEquals("trigger", body.get("event_action").getAsString());
            Assertions.assertTrue(
                body.getAsJsonObject("payload").get("summary").getAsString()
                    .contains("alarmMessage with [DefaultScopeDefine.All]"));
        }
        Assertions.assertTrue(routingKeys.containsAll(Arrays.asList("key-1", "key-2")));
    }

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
            new AlarmRulesWatcher(rules, null, null)
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
