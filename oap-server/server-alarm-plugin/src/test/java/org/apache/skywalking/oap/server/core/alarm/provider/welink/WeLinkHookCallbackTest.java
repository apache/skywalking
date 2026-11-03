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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.skywalking.oap.server.core.alarm.AlarmHttpDeliveryMetrics;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.Rules;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WeLinkHookCallbackTest {
    private static final AtomicBoolean IS_SUCCESS = new AtomicBoolean();
    private static final AtomicInteger COUNT = new AtomicInteger();
    private static final String ACCESS_TOKEN = "5e2584ad-c6ca-4cf9-8513-8216e4759905";
    private static final List<String> AUTHORIZATIONS = new CopyOnWriteArrayList<>();

    @RegisterExtension
    public static final ServerExtension SERVER = new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
            sb.serviceUnder("/welinkhook", (ctx, req) -> HttpResponse.from(
                req.aggregate().thenApply(r -> {
                    final String content = r.content().toStringUtf8();
                    final JsonObject jsonObject = new Gson().fromJson(content, JsonObject.class);

                    String body = "{}";
                    JsonElement clientId = jsonObject.get("client_id");
                    if (clientId != null) {
                        COUNT.incrementAndGet();
                        body = "{\"code\":\"0\",\"message\":\"ok\",\"access_token\":\"" + ACCESS_TOKEN
                            + "\",\"expires_in\":7200}";
                    }
                    JsonElement appMsgId = jsonObject.get("app_msg_id");
                    if (appMsgId != null) {
                        COUNT.incrementAndGet();
                        AUTHORIZATIONS.add(r.headers().get("x-wlk-authorization"));
                    }
                    if (COUNT.get() == 4) {
                        IS_SUCCESS.set(true);
                    }

                    return HttpResponse.of(HttpStatus.OK, MediaType.JSON, body);
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
        WeLinkSettings setting1 = new WeLinkSettings("setting1", AlarmHooksType.welink, true);
        setting1.setWebhooks(webHooks);
        setting1.setTextTemplate(template);
        WeLinkSettings setting2 = new WeLinkSettings("setting2", AlarmHooksType.welink, false);
        setting2.setWebhooks(webHooks);
        setting2.setTextTemplate(template);
        rules.getWeLinkSettingsMap().put(setting1.getFormattedName(), setting1);
        rules.getWeLinkSettingsMap().put(setting2.getFormattedName(), setting2);

        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null, null);
        final DeliveryRecorder recorder = new DeliveryRecorder();
        WeLinkHookCallback welinkHookCallback = new WeLinkHookCallback(
            alarmRulesWatcher, HttpClient.newHttpClient(), Duration.ofSeconds(12),
            new AlarmHttpDeliveryMetrics(recorder));
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
        welinkHookCallback.doAlarm(alarmMessages);
        Assertions.assertTrue(IS_SUCCESS.get());
        Assertions.assertEquals(List.of(ACCESS_TOKEN, ACCESS_TOKEN), AUTHORIZATIONS);
        // 2 token fetches + 2 message posts hit the mock server (COUNT==4), but only message posts are deliveries.
        Assertions.assertEquals(List.of("welink,success", "welink,success"), recorder.deliveryKeys);
    }

    @Test
    public void tokenFetchIoFailureIsRecordedAsTokenError() throws Exception {
        final ServerSocket closed = new ServerSocket();
        closed.bind(new InetSocketAddress("127.0.0.1", 0));
        final int port = closed.getLocalPort();
        closed.close();

        final DeliveryRecorder recorder = new DeliveryRecorder();
        Assertions.assertThrows(IOException.class, () -> doAlarmTo("http://127.0.0.1:" + port, recorder));
        Assertions.assertEquals(List.of("welink,token_error"), recorder.deliveryKeys);
        Assertions.assertEquals(List.of(), recorder.statusKeys);
    }

    @Test
    public void tokenResponseWithoutTokenIsRecordedByReasonAndSkipsMessages() throws Exception {
        assertTokenFailure(200, "{\"code\":\"10007\",\"message\":\"clientId or clientSecret error\"}",
                           "welink,auth_failed", "welink,2xx");
        assertTokenFailure(200, "{\"code\":\"48503\",\"message\":\"too many requests\"}",
                           "welink,rate_limited", "welink,2xx");
        assertTokenFailure(200, "{}", "welink,rejected", "welink,2xx");
        // A gateway in front of the token endpoint answers with an HTML page.
        assertTokenFailure(200, "<html><body>maintenance</body></html>", "welink,token_error", "welink,2xx");
        assertTokenFailure(502, "<html><body>502 Bad Gateway</body></html>", "welink,server_error", "welink,5xx");
    }

    private static void assertTokenFailure(final int status,
                                           final String body,
                                           final String deliveryKey,
                                           final String statusKey) throws Exception {
        final AtomicInteger messages = new AtomicInteger();
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> respond(exchange, status, body));
        server.createContext("/message", exchange -> {
            messages.incrementAndGet();
            respond(exchange, 200, "{\"code\":\"0\",\"message\":\"ok\"}");
        });
        server.start();
        try {
            final DeliveryRecorder recorder = new DeliveryRecorder();
            doAlarmTo("http://127.0.0.1:" + server.getAddress().getPort(), recorder);
            Assertions.assertEquals(List.of(deliveryKey), recorder.deliveryKeys, body);
            Assertions.assertEquals(List.of(statusKey), recorder.statusKeys, body);
            Assertions.assertEquals(0, messages.get(), body);
        } finally {
            server.stop(0);
        }
    }

    private static void respond(final HttpExchange exchange, final int status, final String body) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void doAlarmTo(final String baseUrl, final DeliveryRecorder recorder) throws Exception {
        final List<WeLinkSettings.WebHookUrl> webHooks = new ArrayList<>();
        webHooks.add(new WeLinkSettings.WebHookUrl(
            "clientId", "clientSecret", baseUrl + "/token", baseUrl + "/message", "robotName", "1"
        ));
        final Rules rules = new Rules();
        final WeLinkSettings setting = new WeLinkSettings("setting1", AlarmHooksType.welink, true);
        setting.setWebhooks(webHooks);
        setting.setTextTemplate("Apache SkyWalking Alarm: \n %s.");
        rules.getWeLinkSettingsMap().put(setting.getFormattedName(), setting);

        final WeLinkHookCallback callback = new WeLinkHookCallback(
            new AlarmRulesWatcher(rules, null, null),
            HttpClient.newHttpClient(),
            Duration.ofSeconds(2),
            new AlarmHttpDeliveryMetrics(recorder)
        );

        final AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setScopeId(DefaultScopeDefine.SERVICE);
        alarmMessage.setRuleName("service_resp_time_rule");
        alarmMessage.setAlarmMessage("alarm");
        alarmMessage.getHooks().add(setting.getFormattedName());
        callback.doAlarm(List.of(alarmMessage));
    }

    private static final class DeliveryRecorder extends MetricsCreatorNoop {
        private final List<String> deliveryKeys = new CopyOnWriteArrayList<>();
        private final List<String> statusKeys = new CopyOnWriteArrayList<>();

        @Override
        public CounterMetrics createCounter(final String name, final String tips, final MetricsTag.Keys tagKeys,
                                            final MetricsTag.Values tagValues) {
            final String joined = String.join(",", tagValues.getValues());
            return new CounterMetrics() {
                @Override
                public void inc() {
                    if ("alarm_http_delivery_total".equals(name)) {
                        deliveryKeys.add(joined);
                    } else if ("alarm_http_delivery_status_total".equals(name)) {
                        statusKeys.add(joined);
                    }
                }

                @Override
                public void inc(final double value) {
                    inc();
                }
            };
        }
    }

    @Test
    public void errorCodesInMessageResponseAreClassified() {
        final WeLinkHookCallback callback = new WeLinkHookCallback(
            new AlarmRulesWatcher(new Rules(), null, null), HttpClient.newHttpClient(), Duration.ofSeconds(12),
            new AlarmHttpDeliveryMetrics(new MetricsCreatorNoop()));

        Assertions.assertEquals(AlarmHttpDeliveryMetrics.RESULT_SUCCESS, callback.classifyAcceptedBody(
            "{\"code\":\"0\",\"message\":\"ok\"}"));
        Assertions.assertEquals(AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED, callback.classifyAcceptedBody(
            "{\"code\":\"1000\",\"message\":\"access_token expired\"}"));
        Assertions.assertEquals(AlarmHttpDeliveryMetrics.RESULT_RATE_LIMITED, callback.classifyAcceptedBody(
            "{\"code\":\"48503\",\"message\":\"too many requests\"}"));
        Assertions.assertEquals(AlarmHttpDeliveryMetrics.RESULT_REJECTED, callback.classifyAcceptedBody(
            "{\"code\":\"12345\",\"message\":\"invalid group\"}"));
    }
}
