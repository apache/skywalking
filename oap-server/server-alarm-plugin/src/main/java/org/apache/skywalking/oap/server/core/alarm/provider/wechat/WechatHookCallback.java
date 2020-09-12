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
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;

import java.io.IOException;
import java.util.List;

/**
 * Use SkyWalking alarm wechat webhook API.
 */
@Slf4j
public class WechatHookCallback implements AlarmCallback {
    private static final int HTTP_CONNECT_TIMEOUT = 1000;
    private static final int HTTP_CONNECTION_REQUEST_TIMEOUT = 1000;
    private static final int HTTP_SOCKET_TIMEOUT = 10000;
    private AlarmRulesWatcher alarmRulesWatcher;
    private RequestConfig requestConfig;

    public WechatHookCallback(final AlarmRulesWatcher alarmRulesWatcher) {
        this.alarmRulesWatcher = alarmRulesWatcher;
        this.requestConfig = RequestConfig.custom()
                .setConnectTimeout(HTTP_CONNECT_TIMEOUT)
                .setConnectionRequestTimeout(HTTP_CONNECTION_REQUEST_TIMEOUT)
                .setSocketTimeout(HTTP_SOCKET_TIMEOUT)
                .build();
    }

    @Override
    public void doAlarm(List<AlarmMessage> alarmMessages) {
        if (this.alarmRulesWatcher.getWechatSettings() == null || this.alarmRulesWatcher.getWechatSettings().getWebhooks().isEmpty()) {
            return;
        }
        CloseableHttpClient httpClient = HttpClients.custom().build();
        try {
            this.alarmRulesWatcher.getWechatSettings().getWebhooks().forEach(url -> {
                alarmMessages.forEach(alarmMessage -> {
                    String requestBody = String.format(
                            this.alarmRulesWatcher.getWechatSettings().getTextTemplate(), alarmMessage.getAlarmMessage()
                    );
                    sendAlarmMessage(httpClient, url, requestBody);
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

    private void sendAlarmMessage(CloseableHttpClient httpClient, String url, String requestBody) {
        try {
            HttpPost post = new HttpPost(url);
            post.setConfig(requestConfig);
            post.setHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON.toString());
            post.setHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON.toString());
            StringEntity entity = new StringEntity(requestBody, ContentType.APPLICATION_JSON);
            post.setEntity(entity);
            CloseableHttpResponse httpResponse = httpClient.execute(post);
            StatusLine statusLine = httpResponse.getStatusLine();
            if (statusLine != null && statusLine.getStatusCode() != HttpStatus.SC_OK) {
                log.error("send wechat alarm to {} failure. Response code: {} ", url, statusLine.getStatusCode());
            }
        } catch (Throwable e) {
            log.error("send wechat alarm to {} failure.", url, e);
        }
    }
}
