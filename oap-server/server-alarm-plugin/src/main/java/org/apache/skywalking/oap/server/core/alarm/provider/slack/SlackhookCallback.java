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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.HttpAlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.skywalking.oap.server.library.util.CollectionUtils;

/**
 * Use SkyWalking alarm slack webhook API calls a remote endpoints.
 */
@Slf4j
@RequiredArgsConstructor
public class SlackhookCallback extends HttpAlarmCallback {
    private static final Gson GSON = new Gson();

    private final AlarmRulesWatcher alarmRulesWatcher;

    @Override
    public void doAlarmCallback(List<AlarmMessage> alarmMessages, boolean isRecovery) throws Exception {
        Map<String, SlackSettings> settingsMap = alarmRulesWatcher.getSlackSettings();
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

            for (final var url : setting.getWebhooks()) {
                final var jsonObject = new JsonObject();
                final var jsonElements = new JsonArray();
                for (AlarmMessage item : messages) {
                    jsonElements.add(GSON.fromJson(
                            String.format(
                                    getTemplate(setting, isRecovery), item.getAlarmMessage()
                            ), JsonObject.class));
                }
                jsonObject.add("blocks", jsonElements);
                final var body = GSON.toJson(jsonObject);
                try {
                    post(URI.create(url), body, Map.of());
                } catch (Exception e) {
                    log.error("Failed to send alarm message to Slack: {}", url, e);
                }
            }
        }
    }

    private String getTemplate(SlackSettings setting, boolean isRecovery) {
        return isRecovery ? setting.getRecoveryTextTemplate() : setting.getTextTemplate();
    }
}
