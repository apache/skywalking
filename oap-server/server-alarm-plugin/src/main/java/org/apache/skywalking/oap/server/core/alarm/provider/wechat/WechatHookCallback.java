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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmHttpDeliveryMetrics;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.HttpAlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Use SkyWalking alarm wechat webhook API.
 */
@Slf4j
public class WechatHookCallback extends HttpAlarmCallback {
    /**
     * Error codes of the WeCom group robot API. Any other code is {@code rejected}. WeCom asks callers to read
     * {@code errcode} only, as its {@code errmsg} texts may change.
     */
    private static final Map<String, String> ERROR_CODE_RESULTS = Map.of(
        "-1", AlarmHttpDeliveryMetrics.RESULT_SERVER_ERROR,
        "93000", AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED,
        "93001", AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED,
        "93004", AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED,
        "93008", AlarmHttpDeliveryMetrics.RESULT_AUTH_FAILED,
        "45009", AlarmHttpDeliveryMetrics.RESULT_RATE_LIMITED,
        "45033", AlarmHttpDeliveryMetrics.RESULT_RATE_LIMITED
    );

    private final AlarmRulesWatcher alarmRulesWatcher;

    public WechatHookCallback(final AlarmRulesWatcher alarmRulesWatcher, final HttpClient httpClient,
                              final Duration httpTimeout, final AlarmHttpDeliveryMetrics deliveryMetrics) {
        super(httpClient, httpTimeout, deliveryMetrics, AlarmHooksType.wechat.name());
        this.alarmRulesWatcher = alarmRulesWatcher;
    }

    @Override
    protected String classifyAcceptedBody(final String body) {
        return resultOfBodyCode(body, "errcode", ERROR_CODE_RESULTS);
    }

    @Override
    public void doAlarmCallback(List<AlarmMessage> alarmMessages, boolean isRecovery) throws Exception {
        Map<String, WechatSettings> settingsMap = alarmRulesWatcher.getWechatSettings();
        if (settingsMap == null || settingsMap.isEmpty()) {
            return;
        }

        Map<String, List<AlarmMessage>> groupedMessages = groupMessagesByHook(alarmMessages);
        for (Map.Entry<String, List<AlarmMessage>> entry : groupedMessages.entrySet()) {
            var hookName = entry.getKey();
            var messages = entry.getValue();
            var setting = settingsMap.get(hookName);
            if (setting == null || CollectionUtils.isEmpty(setting.getWebhooks()) || CollectionUtils.isEmpty(messages)) {
                continue;
            }
            String template = getTemplate(setting, isRecovery);
            if (StringUtil.isBlank(template)) {
                continue;
            }
            for (final var url : setting.getWebhooks()) {
                for (final var alarmMessage : messages) {
                    final var requestBody = String.format(template, alarmMessage.getAlarmMessage());
                    try {
                        post(URI.create(url), requestBody, Map.of());
                    } catch (Exception e) {
                        log.error("Failed to send alarm message to Wechat webhook: {}", url, e);
                    }
                }
            }
        }
    }

    private String getTemplate(WechatSettings setting, boolean isRecovery) {
        return isRecovery ? setting.getRecoveryTextTemplate() : setting.getTextTemplate();
    }
}
