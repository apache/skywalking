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
import io.netty.handler.codec.http.HttpHeaderValues;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Use SkyWalking alarm feishu webhook API.
 */
@Slf4j
public class FeishuHookCallback implements AlarmCallback {

    private static final int HTTP_CONNECT_TIMEOUT = 1000;
    private static final int HTTP_CONNECTION_REQUEST_TIMEOUT = 1000;
    private static final int HTTP_SOCKET_TIMEOUT = 10000;
    private AlarmRulesWatcher alarmRulesWatcher;
    private RequestConfig requestConfig;

    public FeishuHookCallback(final AlarmRulesWatcher alarmRulesWatcher) {
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
        if (this.alarmRulesWatcher.getFeishuSettings() == null || this.alarmRulesWatcher.getFeishuSettings().getWebhooks().isEmpty()) {
            return;
        }
        CloseableHttpClient httpClient = HttpClients.custom().build();
        try {
            FeishuSettings feishuSettings = this.alarmRulesWatcher.getFeishuSettings();
            feishuSettings.getWebhooks().forEach(webHookUrl -> {
                alarmMessages.forEach(alarmMessage -> {
                    String requestBody = getRequestBody(webHookUrl, alarmMessage);
                    sendAlarmMessage(httpClient, webHookUrl.getUrl(), requestBody);
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
     * deal requestBody,if has sign set the sign
     */
    private String getRequestBody(FeishuSettings.WebHookUrl webHookUrl, AlarmMessage alarmMessage) {
        String requestBody = String.format(
                this.alarmRulesWatcher.getFeishuSettings().getTextTemplate(), alarmMessage.getAlarmMessage()
        );
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(requestBody, JsonObject.class);
        Map<String, Object> content = buildContent(jsonObject);
        if (!StringUtil.isBlank(webHookUrl.getSecret())) {
            Long timestamp = System.currentTimeMillis() / 1000;
            content.put("timestamp", timestamp);
            try {
                content.put("sign", sign(timestamp, webHookUrl.getSecret()));
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e);
            }
        }
        return gson.toJson(content);
    }

    /**
     * build content,if has ats someone set the ats
     */
    private Map<String, Object> buildContent(JsonObject jsonObject) {
        Map<String, Object> content = new HashMap<>();
        content.put("msg_type", jsonObject.get("msg_type").getAsString());
        if (jsonObject.get("ats") != null) {
            String ats = jsonObject.get("ats").getAsString();
            String text = jsonObject.get("content").getAsJsonObject().get("text").getAsString();
            List<String> collect = Arrays.stream(ats.split(","))
                    .map(String::trim).collect(Collectors.toList());
            for (String userId : collect) {
                text += "<at user_id=\"" + userId + "\"></at>";
            }
            jsonObject.get("content").getAsJsonObject().addProperty("text", text);
        }
        content.put("content", jsonObject.get("content").getAsJsonObject());
        return content;
    }

    /**
     * Sign webhook url using HmacSHA256 algorithm
     */
    private String sign(final Long timestamp, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(), "HmacSHA256"));
        byte[] signData = mac.doFinal();
        return Base64.encodeBase64String(signData);
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
                log.error("send feishu alarm to {} failure. Response code: {}, Response content: {}", url, statusLine.getStatusCode(),
                        EntityUtils.toString(httpResponse.getEntity()));
            }
        } catch (Throwable e) {
            log.error("send feishu alarm to {} failure.", url, e);
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
