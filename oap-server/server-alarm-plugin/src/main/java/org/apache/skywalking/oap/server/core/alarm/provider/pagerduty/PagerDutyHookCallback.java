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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.netty.handler.codec.http.HttpHeaderValues;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
public class PagerDutyHookCallback implements AlarmCallback {
    private static final String PAGER_DUTY_EVENTS_API_V2_URL = "https://events.pagerduty.com/v2/enqueue";
    private static final int HTTP_CONNECT_TIMEOUT = 1000;
    private static final int HTTP_CONNECTION_REQUEST_TIMEOUT = 1000;
    private static final int HTTP_SOCKET_TIMEOUT = 10000;
    private static final Gson GSON = new Gson();

    private AlarmRulesWatcher alarmRulesWatcher;
    private RequestConfig requestConfig;

    public PagerDutyHookCallback(final AlarmRulesWatcher alarmRulesWatcher) {
        this.alarmRulesWatcher = alarmRulesWatcher;
        this.requestConfig = RequestConfig.custom()
                .setConnectTimeout(HTTP_CONNECT_TIMEOUT)
                .setConnectionRequestTimeout(HTTP_CONNECTION_REQUEST_TIMEOUT)
                .setSocketTimeout(HTTP_SOCKET_TIMEOUT)
                .build();
    }

    @Override
    public void doAlarm(List<AlarmMessage> alarmMessages) {
        if (this.alarmRulesWatcher.getPagerDutySettings() == null || this.alarmRulesWatcher.getPagerDutySettings().getIntegrationKeys().isEmpty()) {
            return;
        }

        CloseableHttpClient httpClient = HttpClients.custom().build();
        try {
            this.alarmRulesWatcher.getPagerDutySettings().getIntegrationKeys().forEach(integrationKey -> {
                alarmMessages.forEach(alarmMessage -> {
                    sendAlarmMessage(httpClient, alarmMessage, integrationKey);
                });
            });
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    private void sendAlarmMessage(CloseableHttpClient httpClient, AlarmMessage alarmMessage, String integrationKey) {
        CloseableHttpResponse httpResponse = null;
        try {
            HttpPost post = new HttpPost(PAGER_DUTY_EVENTS_API_V2_URL);
            post.setConfig(requestConfig);
            post.setHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON.toString());
            post.setHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON.toString());
            post.setEntity(
                    getStringEntity(alarmMessage, integrationKey)
            );
            httpResponse = httpClient.execute(post);
            StatusLine statusLine = httpResponse.getStatusLine();
            if (statusLine != null && statusLine.getStatusCode() != HttpStatus.SC_ACCEPTED) {
                log.error("send PagerDuty alarm to {} failure. Response code: {}, message: {} ",
                        PAGER_DUTY_EVENTS_API_V2_URL, statusLine.getStatusCode(),
                        EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8)
                );
            }
        } catch (Throwable e) {
            log.error("send PagerDuty alarm to {} failure.", PAGER_DUTY_EVENTS_API_V2_URL, e);
        } finally {
            if (httpResponse != null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    private StringEntity getStringEntity(AlarmMessage alarmMessage, String integrationKey) throws UnsupportedEncodingException {
        JsonObject body = new JsonObject();
        JsonObject payload = new JsonObject();
        payload.add("summary", new JsonPrimitive(getFormattedMessage(alarmMessage)));
        payload.add("severity", new JsonPrimitive("warning"));
        payload.add("source", new JsonPrimitive("Skywalking"));
        body.add("payload", payload);
        body.add("routing_key", new JsonPrimitive(integrationKey));
        body.add("dedup_key", new JsonPrimitive(UUID.randomUUID().toString()));
        body.add("event_action", new JsonPrimitive("trigger"));

        return new StringEntity(GSON.toJson(body), ContentType.APPLICATION_JSON);
    }

    private String getFormattedMessage(AlarmMessage alarmMessage) {
        return String.format(
                this.alarmRulesWatcher.getPagerDutySettings().getTextTemplate(), alarmMessage.getAlarmMessage()
        );
    }
}
