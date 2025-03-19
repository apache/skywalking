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

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.RequestHeaders;
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
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WebhookCallbackTest {
    private static final AtomicBoolean IS_SUCCESS = new AtomicBoolean();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @RegisterExtension
    public static final ServerExtension SERVER = new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
            sb.service("/webhook/receiveAlarm", (ctx, req) -> HttpResponse.from(req.aggregate().thenApply(r -> {
                final String content = r.content().toStringUtf8();
                final RequestHeaders headers = r.headers();
                List<AlarmMessage> alarmMessages = new Gson().fromJson(content, new TypeToken<ArrayList<AlarmMessage>>() {
                }.getType());
                if (alarmMessages.size() != 1) {
                    IS_SUCCESS.set(false);
                    return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
                }
                if (Objects.equals(alarmMessages.get(0).getId0(), "1")) {
                    IS_SUCCESS.set(true);
                    COUNTER.incrementAndGet();
                    return HttpResponse.of(HttpStatus.OK);
                } else if (Objects.equals(alarmMessages.get(0).getId0(), "2")) {
                    if (Objects.equals(headers.get("Authorization"), "Bearer bearer_token")
                            && Objects.equals(headers.get("x-company-header"), "arbitrary-additional-http-headers")) {
                        IS_SUCCESS.set(true);
                        COUNTER.incrementAndGet();
                        return HttpResponse.of(HttpStatus.OK);
                    }
                }
                IS_SUCCESS.set(false);
                return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
            })));
        }
    };

    @Test
    public void testWebhook() throws Exception {
        List<String> remoteEndpoints = new ArrayList<>();
        remoteEndpoints.add("http://127.0.0.1:" + SERVER.httpPort() + "/webhook/receiveAlarm");
        Rules rules = new Rules();
        WebhookSettings setting1 = new WebhookSettings("setting1", AlarmHooksType.webhook, true);
        setting1.setUrls(remoteEndpoints);
        WebhookSettings setting2 = new WebhookSettings("setting2", AlarmHooksType.webhook, false);
        setting2.setUrls(remoteEndpoints);
        rules.getWebhookSettingsMap().put(setting1.getFormattedName(), setting1);
        rules.getWebhookSettingsMap().put(setting2.getFormattedName(), setting2);
        setting2.setHeaders(ImmutableMap.of("Authorization", " Bearer bearer_token", "x-company-header", "arbitrary-additional-http-headers"));
        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null, null);
        WebhookCallback webhookCallback = new WebhookCallback(alarmRulesWatcher);
        List<AlarmMessage> alarmMessages = new ArrayList<>(2);
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setId0("1");
        alarmMessage.setScopeId(DefaultScopeDefine.SERVICE);
        alarmMessage.setRuleName("service_resp_time_rule");
        alarmMessage.setAlarmMessage("alarmMessage with [DefaultScopeDefine.All]");
        alarmMessage.getHooks().add(setting1.getFormattedName());
        alarmMessages.add(alarmMessage);
        AlarmMessage anotherAlarmMessage = new AlarmMessage();
        anotherAlarmMessage.setId0("2");
        anotherAlarmMessage.setRuleName("service_resp_time_rule_2");
        anotherAlarmMessage.setScopeId(DefaultScopeDefine.ENDPOINT);
        anotherAlarmMessage.setAlarmMessage("anotherAlarmMessage with [DefaultScopeDefine.Endpoint]");
        anotherAlarmMessage.getHooks().add(setting2.getFormattedName());
        alarmMessages.add(anotherAlarmMessage);
        webhookCallback.doAlarm(alarmMessages);

        Assertions.assertTrue(IS_SUCCESS.get());
        Assertions.assertEquals(2, COUNTER.get());
    }
}