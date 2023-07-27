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

package org.apache.skywalking.oap.server.core.alarm.provider.webhook;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.Rules;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebhookCallbackTest {
    private static final AtomicBoolean IS_SUCCESS = new AtomicBoolean();

    @RegisterExtension
    public static final ServerExtension SERVER = new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
            sb.service("/webhook/receiveAlarm", (ctx, req) -> HttpResponse.from(
                req.aggregate().thenApply(r -> {
                    final String content = r.content().toStringUtf8();
                    final JsonArray elements = new Gson().fromJson(content, JsonArray.class);
                    if (elements.size() == 2) {
                        IS_SUCCESS.set(true);
                        return HttpResponse.of(HttpStatus.OK);
                    }

                    return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
                }))
            );
        }
    };

    @Test
    public void testWebhook() throws IOException, InterruptedException {
        List<String> remoteEndpoints = new ArrayList<>();
        remoteEndpoints.add("http://127.0.0.1:" + SERVER.httpPort() + "/webhook/receiveAlarm");
        Rules rules = new Rules();
        WebhookSettings setting1 = new WebhookSettings("setting1", AlarmHooksType.wechatHooks, true);
        setting1.setUrls(remoteEndpoints);
        WebhookSettings setting2 = new WebhookSettings("setting2", AlarmHooksType.wechatHooks, false);
        setting2.setUrls(remoteEndpoints);
        rules.getWebhookSettingsMap().put(setting1.getFormattedName(), setting1);
        rules.getWebhookSettingsMap().put(setting2.getFormattedName(), setting2);
        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null);
        WebhookCallback webhookCallback = new WebhookCallback(alarmRulesWatcher);
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
        webhookCallback.doAlarm(alarmMessages);

        Assertions.assertTrue(IS_SUCCESS.get());
    }
}
