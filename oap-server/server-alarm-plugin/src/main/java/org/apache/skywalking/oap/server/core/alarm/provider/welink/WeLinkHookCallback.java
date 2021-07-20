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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.handler.codec.http.HttpHeaderValues;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

/**
 * Use SkyWalking alarm WeLink webhook API.
 */
@Slf4j
public class WeLinkHookCallback implements AlarmCallback {

    private static final int HTTP_CONNECT_TIMEOUT = 1000;
    private static final int HTTP_CONNECTION_REQUEST_TIMEOUT = 1000;
    private static final int HTTP_SOCKET_TIMEOUT = 10000;
    private final AlarmRulesWatcher alarmRulesWatcher;
    private final RequestConfig requestConfig;

    public WeLinkHookCallback(final AlarmRulesWatcher alarmRulesWatcher) {
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
        if (this.alarmRulesWatcher.getWeLinkSettings() == null || this.alarmRulesWatcher.getWeLinkSettings()
                                                                                        .getWebhooks()
                                                                                        .isEmpty()) {
            return;
        }
        WeLinkSettings welinkSettings = this.alarmRulesWatcher.getWeLinkSettings();
        welinkSettings.getWebhooks().forEach(webHookUrl -> {
            String accessToken = getAccessToken(webHookUrl);
            alarmMessages.forEach(alarmMessage -> {
                String content = String.format(
                    Locale.US,
                    this.alarmRulesWatcher.getWeLinkSettings().getTextTemplate(),
                    alarmMessage.getAlarmMessage()
                );
                sendAlarmMessage(webHookUrl, accessToken, content);
            });
        });
    }

    /**
     * Send alarm message to remote endpoint
     */
    private void sendAlarmMessage(WeLinkSettings.WebHookUrl webHookUrl, String accessToken, String content) {
        JsonObject appServiceInfo = new JsonObject();
        appServiceInfo.addProperty("app_service_id", "1");
        appServiceInfo.addProperty("app_service_name", webHookUrl.getRobotName());
        JsonArray groupIds = new JsonArray();
        Arrays.stream(webHookUrl.getGroupIds().split(",")).forEach(groupIds::add);
        JsonObject body = new JsonObject();
        body.add("app_service_info", appServiceInfo);
        body.addProperty("app_msg_id", UUID.randomUUID().toString());
        body.add("group_id", groupIds);
        body.addProperty("content", String.format(
            Locale.US, "<r><n></n><g>0</g><c>&lt;imbody&gt;&lt;imagelist/&gt;" +
                "&lt;html&gt;&lt;![CDATA[&lt;DIV&gt;%s&lt;/DIV&gt;]]&gt;&lt;/html&gt;&lt;content&gt;&lt;![CDATA[%s]]&gt;&lt;/content&gt;&lt;/imbody&gt;</c></r>",
            content, content
        ));
        body.addProperty("content_type", 0);
        body.addProperty("client_app_id", "1");
        sendPostRequest(
            webHookUrl.getMessageUrl(), Collections.singletonMap("x-wlk-Authorization", accessToken), body.toString());
    }

    /**
     * Get access token from remote endpoint
     */
    private String getAccessToken(WeLinkSettings.WebHookUrl webHookUrl) {
        String accessTokenUrl = webHookUrl.getAccessTokenUrl();
        String clientId = webHookUrl.getClientId();
        String clientSecret = webHookUrl.getClientSecret();
        String response = sendPostRequest(
            accessTokenUrl, Collections.emptyMap(),
            String.format(Locale.US, "{\"client_id\":%s,\"client_secret\":%s}", clientId, clientSecret)
        );
        Gson gson = new Gson();
        JsonObject responseJson = gson.fromJson(response, JsonObject.class);
        return Optional.ofNullable(responseJson)
                       .map(r -> r.get("access_token"))
                       .map(JsonElement::getAsString)
                       .orElse("");
    }

    /**
     * Post rest invoke
     */
    private String sendPostRequest(String url, Map<String, String> headers, String requestBody) {
        String response = "";
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(url);
            post.setConfig(requestConfig);
            post.setHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON.toString());
            post.setHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON.toString());
            headers.forEach(post::setHeader);
            StringEntity entity = new StringEntity(requestBody, ContentType.APPLICATION_JSON);
            post.setEntity(entity);
            try (CloseableHttpResponse httpResponse = httpClient.execute(post)) {
                StatusLine statusLine = httpResponse.getStatusLine();
                if (statusLine != null) {
                    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
                        log.error("send to {} failure. Response code: {}, Response content: {}", url,
                                  statusLine.getStatusCode(),
                                  EntityUtils.toString(httpResponse.getEntity())
                        );
                    } else {
                        response = EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8);
                    }
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return response;
    }
}
