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

package org.apache.skywalking.oap.server.core.alarm.provider.slack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.handler.codec.http.HttpHeaderValues;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
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
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;

/**
 * Use SkyWalking alarm slack webhook API call a remote endpoints.
 */
@Slf4j
public class SlackhookCallback implements AlarmCallback {
    private static final int HTTP_CONNECT_TIMEOUT = 1000;
    private static final int HTTP_CONNECTION_REQUEST_TIMEOUT = 1000;
    private static final int HTTP_SOCKET_TIMEOUT = 10000;
    private static final Gson GSON = new Gson();
    private AlarmRulesWatcher alarmRulesWatcher;
    private RequestConfig requestConfig;

    public SlackhookCallback(final AlarmRulesWatcher alarmRulesWatcher) {
        this.alarmRulesWatcher = alarmRulesWatcher;
        this.requestConfig = RequestConfig.custom()
                                          .setConnectTimeout(HTTP_CONNECT_TIMEOUT)
                                          .setConnectionRequestTimeout(HTTP_CONNECTION_REQUEST_TIMEOUT)
                                          .setSocketTimeout(HTTP_SOCKET_TIMEOUT)
                                          .build();
    }

    @Override
    public void doAlarm(List<AlarmMessage> alarmMessages) {
        if (this.alarmRulesWatcher.getSlackSettings() == null || this.alarmRulesWatcher.getSlackSettings().getWebhooks().isEmpty()) {
            return;
        }

        CloseableHttpClient httpClient = HttpClients.custom().build();
        try {
            this.alarmRulesWatcher.getSlackSettings().getWebhooks().forEach(url -> {
                HttpPost post = new HttpPost(url);
                post.setConfig(requestConfig);
                post.setHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON.toString());
                post.setHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON.toString());

                StringEntity entity;
                CloseableHttpResponse httpResponse = null;
                try {
                    JsonObject jsonObject = new JsonObject();
                    JsonArray jsonElements = new JsonArray();
                    alarmMessages.forEach(item -> {
                        jsonElements.add(GSON.fromJson(
                            String.format(
                                this.alarmRulesWatcher.getSlackSettings().getTextTemplate(), item.getAlarmMessage()
                            ), JsonObject.class));
                    });
                    jsonObject.add("blocks", jsonElements);
                    entity = new StringEntity(GSON.toJson(jsonObject), ContentType.APPLICATION_JSON);
                    post.setEntity(entity);
                    httpResponse = httpClient.execute(post);
                    StatusLine statusLine = httpResponse.getStatusLine();
                    if (statusLine != null && statusLine.getStatusCode() != HttpStatus.SC_OK) {
                        log.error("Send slack alarm to {} failure. Response code: {}", url , statusLine.getStatusCode());
                    }
                } catch (UnsupportedEncodingException e) {
                    log.error("Alarm to JSON error, {} ", e.getMessage(), e);
                } catch (IOException e) {
                    log.error("Send slack alarm to {} failure.", url , e);
                } finally {
                    if (httpResponse != null) {
                        try {
                            httpResponse.close();
                        } catch (IOException e) {
                            log.error(e.getMessage(), e);
                        }

                    }
                }
            });
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
