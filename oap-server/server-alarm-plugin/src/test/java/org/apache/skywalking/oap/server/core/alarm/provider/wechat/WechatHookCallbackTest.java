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

package org.apache.skywalking.oap.server.core.alarm.provider.wechat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecoveryMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.Rules;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WechatHookCallbackTest {
    public static final String RECOVERED = "[Recovered]";
    private static final AtomicBoolean IS_SUCCESS = new AtomicBoolean();
    private static final AtomicInteger COUNT = new AtomicInteger();
    private static final AtomicInteger RECOVERY_COUNT = new AtomicInteger();

    @RegisterExtension
    public static final ServerExtension SERVER = new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
            sb.service("/wechathook/receiveAlarm", (ctx, req) -> HttpResponse.from(
                    req.aggregate().thenApply(r -> {
                        final String content = r.content().toStringUtf8();
                        final JsonObject jsonObject = new Gson().fromJson(content, JsonObject.class);
                        final String type = jsonObject.get("msgtype").getAsString();
                        if (type.equalsIgnoreCase("text")) {
                            COUNT.incrementAndGet();
                            final String textContent = ((JsonObject) jsonObject.get("text")).get("content").getAsString();
                            if (textContent.startsWith(RECOVERED)) {
                                RECOVERY_COUNT.incrementAndGet();
                            }
                            if (COUNT.get() == 3 && RECOVERY_COUNT.get() == 1) {
                                IS_SUCCESS.set(true);
                            }
                            return HttpResponse.of(HttpStatus.OK);
                        }

                        return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
                    })
            ));
        }
    };

    @Test
    public void testWechatWebhook() throws Exception {
        List<String> remoteEndpoints = new ArrayList<>();
        remoteEndpoints.add("http://127.0.0.1:" + SERVER.httpPort() + "/wechathook/receiveAlarm");
        Rules rules = new Rules();
        String template = "{\"msgtype\":\"text\",\"text\":{\"content\":\"Skywaling alarm: %s\"}}";
        String recoveryTemplate = "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + RECOVERED + "Skywaling alarm: %s\"}}";
        WechatSettings setting1 = new WechatSettings("setting1", AlarmHooksType.wechat, true);
        setting1.setWebhooks(remoteEndpoints);
        setting1.setTextTemplate(template);
        setting1.setRecoveryTextTemplate(recoveryTemplate);
        WechatSettings setting2 = new WechatSettings("setting2", AlarmHooksType.wechat, false);
        setting2.setWebhooks(remoteEndpoints);
        setting2.setTextTemplate(template);
        setting2.setRecoveryTextTemplate(recoveryTemplate);
        rules.getWechatSettingsMap().put(setting1.getFormattedName(), setting1);
        rules.getWechatSettingsMap().put(setting2.getFormattedName(), setting2);
        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null, null);
        WechatHookCallback wechatHookCallback = new WechatHookCallback(alarmRulesWatcher);
        List<AlarmMessage> alarmMessages = new ArrayList<>(2);
        List<AlarmMessage> alarmRecoveryMessages = new ArrayList<>(1);
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
        wechatHookCallback.doAlarm(alarmMessages);
        AlarmRecoveryMessage alarmRecoveryMessage = new AlarmRecoveryMessage(anotherAlarmMessage);
        alarmRecoveryMessages.add(alarmRecoveryMessage);
        wechatHookCallback.doAlarmRecovery(alarmRecoveryMessages);
        Assertions.assertTrue(IS_SUCCESS.get());
    }
}
