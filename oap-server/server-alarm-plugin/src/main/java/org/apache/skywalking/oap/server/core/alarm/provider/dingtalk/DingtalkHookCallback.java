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

package org.apache.skywalking.oap.server.core.alarm.provider.dingtalk;

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
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

/**
 * Use SkyWalking alarm dingtalk webhook API.
 */
@Slf4j
public class DingtalkHookCallback implements AlarmCallback {

    private static final int HTTP_CONNECT_TIMEOUT = 1000;
    private static final int HTTP_CONNECTION_REQUEST_TIMEOUT = 1000;
    private static final int HTTP_SOCKET_TIMEOUT = 10000;
    private AlarmRulesWatcher alarmRulesWatcher;
    private RequestConfig requestConfig;

    public DingtalkHookCallback(final AlarmRulesWatcher alarmRulesWatcher) {
        this.alarmRulesWatcher = alarmRulesWatcher;
        this.requestConfig = RequestConfig.custom()
                .setConnectTimeout(HTTP_CONNECT_TIMEOUT)
                .setConnectionRequestTimeout(HTTP_CONNECTION_REQUEST_TIMEOUT)
                .setSocketTimeout(HTTP_SOCKET_TIMEOUT)
                .build();
    }

    /**
     * Send alarm message if the settings not empty
     */
    @Override
    public void doAlarm(List<AlarmMessage> alarmMessages) {
        if (this.alarmRulesWatcher.getDingtalkSettings() == null || this.alarmRulesWatcher.getDingtalkSettings().getWebhooks().isEmpty()) {
            return;
        }
        CloseableHttpClient httpClient = HttpClients.custom().build();
        try {
            DingtalkSettings dingtalkSettings = this.alarmRulesWatcher.getDingtalkSettings();
            dingtalkSettings.getWebhooks().forEach(webHookUrl -> {
                alarmMessages.forEach(alarmMessage -> {
                    String url = getUrl(webHookUrl);
                    String requestBody = String.format(
                            this.alarmRulesWatcher.getDingtalkSettings().getTextTemplate(), alarmMessage.getAlarmMessage()
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

    /**
     * Get webhook url, sign the url when secret is not empty.
     */
    private String getUrl(DingtalkSettings.WebHookUrl webHookUrl) {
        if (StringUtil.isEmpty(webHookUrl.getSecret())) {
            return webHookUrl.getUrl();
        }
        return getSignUrl(webHookUrl);
    }

    /**
     * Sign webhook url using secret and timestamp
     */
    private String getSignUrl(DingtalkSettings.WebHookUrl webHookUrl) {
        try {
            Long timestamp = System.currentTimeMillis();
            return String.format("%s&timestamp=%s&sign=%s", webHookUrl.getUrl(), timestamp, sign(timestamp, webHookUrl.getSecret()));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sign webhook url using HmacSHA256 algorithm
     */
    private String sign(final Long timestamp, String secret) throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(new String(Base64.getEncoder().encode(signData)), StandardCharsets.UTF_8.name());
    }

    /**
     * Send alarm message to remote endpoint
     */
    private void sendAlarmMessage(CloseableHttpClient httpClient, String url, String requestBody) {
        CloseableHttpResponse httpResponse = null;
        try {
            HttpPost post = new HttpPost(url);
            post.setConfig(requestConfig);
            post.setHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON.toString());
            post.setHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON.toString());
            StringEntity entity = new StringEntity(requestBody, ContentType.APPLICATION_JSON);
            post.setEntity(entity);
            httpResponse = httpClient.execute(post);
            StatusLine statusLine = httpResponse.getStatusLine();
            if (statusLine != null && statusLine.getStatusCode() != HttpStatus.SC_OK) {
                log.error("send dingtalk alarm to {} failure. Response code: {}, Response content: {}", url, statusLine.getStatusCode(),
                        EntityUtils.toString(httpResponse.getEntity()));
            }
        } catch (Throwable e) {
            log.error("send dingtalk alarm to {} failure.", url, e);
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
}
