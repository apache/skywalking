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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.HttpAlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Use SkyWalking alarm feishu webhook API.
 */
@Slf4j
@RequiredArgsConstructor
public class FeishuHookCallback extends HttpAlarmCallback {
    private final AlarmRulesWatcher alarmRulesWatcher;

    protected void doAlarmCallback(List<AlarmMessage> alarmMessages, boolean isRecovery) throws Exception {
        Map<String, FeishuSettings> settingsMap = alarmRulesWatcher.getFeishuSettings();
        if (settingsMap == null || settingsMap.isEmpty()) {
            return;
        }
        Map<String, List<AlarmMessage>> groupedMessages = groupMessagesByHook(alarmMessages);
        for (Map.Entry<String, List<AlarmMessage>> entry : groupedMessages.entrySet()) {
            var hookName = entry.getKey();
            var messages = entry.getValue();
            var setting = settingsMap.get(hookName);
            if (setting == null || CollectionUtils.isEmpty(setting.getWebhooks()) || CollectionUtils.isEmpty(
                    messages)) {
                continue;
            }
            for (final var webHookUrl : setting.getWebhooks()) {
                for (final var alarmMessage : messages) {
                    final var requestBody = getRequestBody(webHookUrl, alarmMessage, getTemplate(setting, isRecovery));
                    try {
                        post(URI.create(webHookUrl.getUrl()), requestBody, Map.of());
                    } catch (Exception e) {
                        log.error("Failed to send alarm message to Feishu: {}", webHookUrl.getUrl(), e);
                    }
                }
            }
        }
    }

    private String getTemplate(FeishuSettings setting, boolean isRecovery) {
        return isRecovery ? setting.getRecoveryTextTemplate() : setting.getTextTemplate();
    }

    /**
     * deal requestBody,if it has sign set the sign
     */
    private String getRequestBody(FeishuSettings.WebHookUrl webHookUrl, AlarmMessage alarmMessage, String textTemplate) {
        final var requestBody = String.format(textTemplate, alarmMessage.getAlarmMessage()
        );
        final var gson = new Gson();
        final var jsonObject = gson.fromJson(requestBody, JsonObject.class);
        final var content = buildContent(jsonObject);
        if (!StringUtil.isBlank(webHookUrl.getSecret())) {
            final var timestamp = System.currentTimeMillis() / 1000;
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
     * build content,if it has ats someone set the ats
     */
    private Map<String, Object> buildContent(JsonObject jsonObject) {
        final var content = new HashMap<String, Object>();
        content.put("msg_type", jsonObject.get("msg_type").getAsString());
        if (jsonObject.get("ats") != null) {
            final var ats = jsonObject.get("ats").getAsString();
            final var collect = Arrays.stream(ats.split(",")).map(String::trim).collect(Collectors.toList());
            var text = jsonObject.get("content").getAsJsonObject().get("text").getAsString();
            for (final var userId : collect) {
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
        final var stringToSign = timestamp + "\n" + secret;
        final var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(), "HmacSHA256"));
        final var signData = mac.doFinal();
        return Base64.getEncoder().encodeToString(signData);
    }

}
