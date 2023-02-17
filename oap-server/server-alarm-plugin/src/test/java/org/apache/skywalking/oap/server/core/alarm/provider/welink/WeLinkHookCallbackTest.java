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

package org.apache.skywalking.oap.server.core.alarm.provider.welink;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
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

public class WeLinkHookCallbackTest {
    private static final AtomicBoolean IS_SUCCESS = new AtomicBoolean();
    private static final AtomicInteger COUNT = new AtomicInteger();

    @RegisterExtension
    public static final ServerExtension SERVER = new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
            sb.serviceUnder("/welinkhook", (ctx, req) -> HttpResponse.from(
                req.aggregate().thenApply(r -> {
                    final String content = r.content().toStringUtf8();
                    final JsonObject jsonObject = new Gson().fromJson(content, JsonObject.class);

                    if (COUNT.get() == 0) {
                        String clientId = jsonObject.get("client_id").getAsString();
                        if (clientId != null) {
                            COUNT.incrementAndGet();
                        }
                    } else if (COUNT.get() >= 1) {
                        String appMsgId = jsonObject.get("app_msg_id").getAsString();
                        if (appMsgId != null) {
                            COUNT.incrementAndGet();
                        }
                    }
                    if (COUNT.get() == 2) {
                        IS_SUCCESS.set(true);
                    }

                    return HttpResponse.of(HttpStatus.OK, MediaType.JSON, "{}");
                })));
        }
    };

    @Test
    public void testWeLinkDoAlarm() throws Exception {
        List<WeLinkSettings.WebHookUrl> webHooks = new ArrayList<>();
        webHooks.add(new WeLinkSettings.WebHookUrl("clientId", "clientSecret",
                                                   "http://127.0.0.1:" + SERVER.httpPort() + "/welinkhook/api/auth/v2/tickets",
                                                   "http://127.0.0.1:" + SERVER.httpPort() + "/welinkhook/api/welinkim/v1/im-service/chat/group-chat",
                                                   "robotName", "1,2,3"
        ));
        Rules rules = new Rules();
        String template = "Apache SkyWalking Alarm: \n %s.";
        rules.setWelinks(WeLinkSettings.builder().webhooks(webHooks).textTemplate(template).build());

        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null);
        WeLinkHookCallback welinkHookCallback = new WeLinkHookCallback(alarmRulesWatcher);
        List<AlarmMessage> alarmMessages = new ArrayList<>(2);
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setScopeId(DefaultScopeDefine.SERVICE);
        alarmMessage.setRuleName("service_resp_time_rule");
        alarmMessage.setAlarmMessage("alarmMessage with [DefaultScopeDefine.All]");
        alarmMessages.add(alarmMessage);
        AlarmMessage anotherAlarmMessage = new AlarmMessage();
        anotherAlarmMessage.setRuleName("service_resp_time_rule_2");
        anotherAlarmMessage.setScopeId(DefaultScopeDefine.ENDPOINT);
        anotherAlarmMessage.setAlarmMessage("anotherAlarmMessage with [DefaultScopeDefine.Endpoint]");
        alarmMessages.add(anotherAlarmMessage);
        welinkHookCallback.doAlarm(alarmMessages);
        Assertions.assertTrue(IS_SUCCESS.get());
    }
}
