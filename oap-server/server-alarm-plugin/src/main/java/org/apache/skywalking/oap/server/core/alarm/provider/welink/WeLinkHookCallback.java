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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.HttpAlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

/**
 * Use SkyWalking alarm WeLink webhook API.
 */
@Slf4j
@RequiredArgsConstructor
public class WeLinkHookCallback extends HttpAlarmCallback {
    private final AlarmRulesWatcher alarmRulesWatcher;

    /**
     * Send alarm message if the settings not empty
     */
    @Override
    public void doAlarm(List<AlarmMessage> alarmMessages) throws Exception {
        Map<String, WeLinkSettings> settingsMap = alarmRulesWatcher.getWeLinkSettings();
        if (settingsMap == null || settingsMap.isEmpty()) {
            return;
        }
        Map<String, List<AlarmMessage>> groupedMessages =  groupMessagesByHook(alarmMessages);
        for (Map.Entry<String, List<AlarmMessage>> entry : groupedMessages.entrySet()) {
            var hookName = entry.getKey();
            var messages = entry.getValue();
            var setting = settingsMap.get(hookName);
            if (setting == null || CollectionUtils.isEmpty(setting.getWebhooks()) || CollectionUtils.isEmpty(
                messages)) {
                continue;
            }
            for (final var webHookUrl : setting.getWebhooks()) {
                final var accessToken = getAccessToken(webHookUrl);
                for (final var alarmMessage : messages) {
                    final var content = String.format(
                        setting.getTextTemplate(),
                        alarmMessage.getAlarmMessage()
                    );
                    sendAlarmMessage(webHookUrl, accessToken, content);
                }
            }
        }
    }

    /**
     * Send alarm message to remote endpoint
     */
    private void sendAlarmMessage(WeLinkSettings.WebHookUrl webHookUrl, String accessToken, String content) throws IOException, InterruptedException {
        final var appServiceInfo = new JsonObject();
        appServiceInfo.addProperty("app_service_id", "1");
        appServiceInfo.addProperty("app_service_name", webHookUrl.getRobotName());
        final var groupIds = new JsonArray();
        Arrays.stream(webHookUrl.getGroupIds().split(",")).forEach(groupIds::add);
        final var body = new JsonObject();
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
        final var requestBody = body.toString();
        post(URI.create(webHookUrl.getMessageUrl()), requestBody, Collections.singletonMap("x-wlk-Authorization", accessToken));
    }

    /**
     * Get access token from remote endpoint
     */
    private String getAccessToken(WeLinkSettings.WebHookUrl webHookUrl) throws IOException, InterruptedException {
        final var accessTokenUrl = webHookUrl.getAccessTokenUrl();
        final var clientId = webHookUrl.getClientId();
        final var clientSecret = webHookUrl.getClientSecret();
        final var response = post(
            URI.create(accessTokenUrl),
            String.format(Locale.US, "{\"client_id\":%s,\"client_secret\":%s}", clientId, clientSecret),
            Collections.emptyMap()
        );
        final var gson = new Gson();
        final var responseJson = gson.fromJson(response, JsonObject.class);
        return Optional.ofNullable(responseJson)
                       .map(r -> r.get("access_token"))
                       .map(JsonElement::getAsString)
                       .orElse("");
    }

}
