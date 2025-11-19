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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.HttpAlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Use SkyWalking alarm dingtalk webhook API.
 */
@Slf4j
@RequiredArgsConstructor
public class DingtalkHookCallback extends HttpAlarmCallback {
    private final AlarmRulesWatcher alarmRulesWatcher;

    protected void doAlarmCallback(List<AlarmMessage> alarmMessages, boolean isRecovery) throws Exception {
        Map<String, DingtalkSettings> settingsMap = alarmRulesWatcher.getDingtalkSettings();
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
            String template = getTemplate(setting, isRecovery);
            if (StringUtil.isBlank(template)) {
                continue;
            }
            for (final var webHookUrl : setting.getWebhooks()) {
                final var url = getUrl(webHookUrl);
                for (final var alarmMessage : messages) {
                    final var requestBody = String.format(template, alarmMessage.getAlarmMessage());
                    post(URI.create(url), requestBody, Map.of());
                }
            }
        }
    }

    private String getTemplate(DingtalkSettings setting, boolean isRecovery) {
        return isRecovery ? setting.getRecoveryTextTemplate() : setting.getTextTemplate();
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
            final var timestamp = System.currentTimeMillis();
            return String.format("%s&timestamp=%s&sign=%s", webHookUrl.getUrl(), timestamp, sign(timestamp, webHookUrl.getSecret()));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sign webhook url using HmacSHA256 algorithm
     */
    private String sign(final Long timestamp, String secret) throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        final var stringToSign = timestamp + "\n" + secret;
        final var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        final var signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(new String(Base64.getEncoder().encode(signData)), StandardCharsets.UTF_8);
    }

}
