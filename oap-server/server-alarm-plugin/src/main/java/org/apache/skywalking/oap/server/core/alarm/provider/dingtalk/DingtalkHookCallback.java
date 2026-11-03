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

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmHttpDeliveryMetrics;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.HttpAlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Use SkyWalking alarm dingtalk webhook API.
 */
@Slf4j
public class DingtalkHookCallback extends HttpAlarmCallback {
    /**
     * Error codes of the custom robot API, besides {@code 310000} and {@code 40035}, which stand for more than one
     * reason and are read with their {@code errmsg}. Any other code is {@code rejected}.
     */
    private static final Map<String, String> ERROR_CODE_RESULTS = Map.of(
        "-1", AlarmHttpDeliveryMetrics.RESULT_SERVER_ERROR,
        "300001", AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED,
        "400101", AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED,
        "400102", AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED,
        "400106", AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED,
        "130101", AlarmHttpDeliveryMetrics.RESULT_RATE_LIMITED,
        "410100", AlarmHttpDeliveryMetrics.RESULT_RATE_LIMITED
    );

    private final AlarmRulesWatcher alarmRulesWatcher;

    public DingtalkHookCallback(final AlarmRulesWatcher alarmRulesWatcher, final HttpClient httpClient,
                                final Duration httpTimeout, final AlarmHttpDeliveryMetrics deliveryMetrics) {
        super(httpClient, httpTimeout, deliveryMetrics, AlarmHooksType.dingtalk.name());
        this.alarmRulesWatcher = alarmRulesWatcher;
    }

    @Override
    protected String classifyAcceptedBody(final String body) {
        return jsonObjectOf(body).map(DingtalkHookCallback::resultOf).orElse(AlarmHttpDeliveryMetrics.RESULT_SUCCESS);
    }

    private static String resultOf(final JsonObject json) {
        // The oapi gateway answers its per-IP limit with {"status":1111} instead of an errcode.
        if (stringOf(json, "status").filter("1111"::equals).isPresent()) {
            return AlarmHttpDeliveryMetrics.RESULT_RATE_LIMITED;
        }
        final String code = stringOf(json, "errcode").orElse("0");
        final String message = stringOf(json, "errmsg").orElse("");
        switch (code) {
            case "0":
                return AlarmHttpDeliveryMetrics.RESULT_SUCCESS;
            case "310000":
                // A failed security setting: a missing keyword, or a bad timestamp, signature or IP whitelist.
                return message.contains("keywords not in content")
                    ? AlarmHttpDeliveryMetrics.RESULT_REJECTED
                    : AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED;
            case "40035":
                // A missing parameter: the access_token of the webhook URL, or the JSON body.
                return message.contains("access_token")
                    ? AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED
                    : AlarmHttpDeliveryMetrics.RESULT_REJECTED;
            default:
                return ERROR_CODE_RESULTS.getOrDefault(code, AlarmHttpDeliveryMetrics.RESULT_REJECTED);
        }
    }

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
