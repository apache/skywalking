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

package org.apache.skywalking.oap.server.core.alarm.provider.feishu;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.Rules;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FeishuHookCallbackTest {
    private static final AtomicBoolean IS_SUCCESS = new AtomicBoolean();
    private static final AtomicBoolean CHECK_SIGN = new AtomicBoolean();
    private static final AtomicInteger COUNT = new AtomicInteger();
    private final String secret = "dummy-secret";

    @RegisterExtension
    public static final ServerExtension SERVER = new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
            sb.service("/feishuhook/receiveAlarm", (ctx, req) -> HttpResponse.from(
                req.aggregate().thenApply(r -> {
                    final String content = r.content().toStringUtf8();
                    final JsonObject jsonObject = new Gson().fromJson(content, JsonObject.class);
                    final String type = jsonObject.get("msg_type").getAsString();
                    if (CHECK_SIGN.get()) {
                        String timestamp = jsonObject.get("timestamp").getAsString();
                        String sign = jsonObject.get("sign").getAsString();
                        if (StringUtil.isEmpty(timestamp) || StringUtil.isEmpty(sign)) {
                            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
                        }
                    }
                    if (type.equalsIgnoreCase("text")) {
                        COUNT.incrementAndGet();
                        if (COUNT.get() == 2) {
                            IS_SUCCESS.set(true);
                        }
                        return HttpResponse.of(HttpStatus.OK);
                    }
                    return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
                })));
        }
    };

    @Test
    public void testFeishuWebhookWithoutSign() throws Exception {
        List<FeishuSettings.WebHookUrl> webHooks = new ArrayList<>();
        webHooks.add(new FeishuSettings.WebHookUrl("", "http://127.0.0.1:" + SERVER.httpPort() + "/feishuhook/receiveAlarm?token=dummy_token"));
        Rules rules = new Rules();
        String template = "{\"msg_type\":\"text\",\"content\":{\"text\":\"Skywaling alarm: %s\"}}";
        FeishuSettings setting1 = new FeishuSettings("setting1", AlarmHooksType.feishu, true);
        setting1.setWebhooks(webHooks);
        setting1.setTextTemplate(template);
        FeishuSettings setting2 = new FeishuSettings("setting2", AlarmHooksType.feishu, false);
        setting2.setWebhooks(webHooks);
        setting2.setTextTemplate(template);
        rules.getFeishuSettingsMap().put(setting1.getFormattedName(), setting1);
        rules.getFeishuSettingsMap().put(setting2.getFormattedName(), setting2);

        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null, null);
        FeishuHookCallback feishuHookCallback = new FeishuHookCallback(alarmRulesWatcher);
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
        feishuHookCallback.doAlarm(alarmMessages);
        Assertions.assertTrue(IS_SUCCESS.get());
    }

    @Test
    public void testFeishuWebhookWithSign() throws Exception {
        CHECK_SIGN.set(true);
        List<FeishuSettings.WebHookUrl> webHooks = new ArrayList<>();
        webHooks.add(new FeishuSettings.WebHookUrl(secret, "http://127.0.0.1:" + SERVER.httpPort() + "/feishuhook/receiveAlarm?token=dummy_token"));
        Rules rules = new Rules();
        String template = "{\"msg_type\":\"text\",\"content\":{\"text\":\"Skywaling alarm: %s\"}}";
        FeishuSettings setting1 = new FeishuSettings("setting1", AlarmHooksType.feishu, true);
        setting1.setWebhooks(webHooks);
        setting1.setTextTemplate(template);
        FeishuSettings setting2 = new FeishuSettings("setting2", AlarmHooksType.feishu, false);
        setting2.setWebhooks(webHooks);
        setting2.setTextTemplate(template);
        rules.getFeishuSettingsMap().put(setting1.getFormattedName(), setting1);
        rules.getFeishuSettingsMap().put(setting2.getFormattedName(), setting2);

        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null, null);
        FeishuHookCallback feishuHookCallback = new FeishuHookCallback(alarmRulesWatcher);
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
        feishuHookCallback.doAlarm(alarmMessages);
        Assertions.assertTrue(IS_SUCCESS.get());
    }

    @Test
    public void testFeishuWebhookWithSignAndAt() throws Exception {
        CHECK_SIGN.set(true);
        List<FeishuSettings.WebHookUrl> webHooks = new ArrayList<>();
        webHooks.add(new FeishuSettings.WebHookUrl(secret, "http://127.0.0.1:" + SERVER.httpPort() + "/feishuhook/receiveAlarm?token=dummy_token"));
        Rules rules = new Rules();
        String template = "{\"msg_type\":\"text\",\"content\":{\"text\":\"Skywaling alarm: %s\"},\"ats\":\"123\"}";
        FeishuSettings setting1 = new FeishuSettings("setting1", AlarmHooksType.feishu, true);
        setting1.setWebhooks(webHooks);
        setting1.setTextTemplate(template);
        FeishuSettings setting2 = new FeishuSettings("setting2", AlarmHooksType.feishu, false);
        setting2.setWebhooks(webHooks);
        setting2.setTextTemplate(template);
        rules.getFeishuSettingsMap().put(setting1.getFormattedName(), setting1);
        rules.getFeishuSettingsMap().put(setting2.getFormattedName(), setting2);

        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null, null);
        FeishuHookCallback feishuHookCallback = new FeishuHookCallback(alarmRulesWatcher);
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
        feishuHookCallback.doAlarm(alarmMessages);
        Assertions.assertTrue(IS_SUCCESS.get());
    }
}
