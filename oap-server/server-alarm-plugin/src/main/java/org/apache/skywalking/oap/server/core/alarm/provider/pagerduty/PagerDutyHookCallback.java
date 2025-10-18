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

package org.apache.skywalking.oap.server.core.alarm.provider.pagerduty;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.HttpAlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.skywalking.oap.server.library.util.CollectionUtils;

@Slf4j
@RequiredArgsConstructor
public class PagerDutyHookCallback extends HttpAlarmCallback {
    private static final String PAGER_DUTY_EVENTS_API_V2_URL = "http://events.pagerduty.com/v2/enqueue";
    private static final Gson GSON = new Gson();

    private final AlarmRulesWatcher alarmRulesWatcher;

    @Override
    protected void doAlarmCallback(List<AlarmMessage> alarmMessages, boolean isRecovery) throws Exception {
        Map<String, PagerDutySettings> settingsMap = alarmRulesWatcher.getPagerDutySettings();
        if (settingsMap == null || settingsMap.isEmpty()) {
            return;
        }

        Map<String, List<AlarmMessage>> groupedMessages = groupMessagesByHook(alarmMessages);
        for (Map.Entry<String, List<AlarmMessage>> entry : groupedMessages.entrySet()) {
            var hookName = entry.getKey();
            var messages = entry.getValue();
            var setting = settingsMap.get(hookName);
            if (setting == null || CollectionUtils.isEmpty(setting.getIntegrationKeys()) || CollectionUtils.isEmpty(
                    messages)) {
                continue;
            }
            for (final var integrationKey : setting.getIntegrationKeys()) {
                for (final var alarmMessage : messages) {
                    try {
                        post(
                                URI.create(PAGER_DUTY_EVENTS_API_V2_URL),
                                getMessageBody(alarmMessage, integrationKey, getTemplate(isRecovery, setting)), Map.of()
                        );
                    } catch (Exception e) {
                        log.error("Failed to send alarm message to PagerDuty: {}", integrationKey, e);
                    }
                }
            }
        }
    }

    private String getTemplate(boolean isRecovery, PagerDutySettings setting) {
        return isRecovery ? setting.getRecoveryTextTemplate() : setting.getTextTemplate();
    }

    private String getMessageBody(AlarmMessage alarmMessage, String integrationKey, String textTemplate) {
        final var body = new JsonObject();
        final var payload = new JsonObject();
        payload.add("summary", new JsonPrimitive(getFormattedMessage(alarmMessage, textTemplate)));
        payload.add("severity", new JsonPrimitive("warning"));
        payload.add("source", new JsonPrimitive("Skywalking"));
        body.add("payload", payload);
        body.add("routing_key", new JsonPrimitive(integrationKey));
        body.add("dedup_key", new JsonPrimitive(UUID.randomUUID().toString()));
        body.add("event_action", new JsonPrimitive("trigger"));

        return GSON.toJson(body);
    }

    private String getFormattedMessage(AlarmMessage alarmMessage, String textTemplate) {
        return String.format(textTemplate, alarmMessage.getAlarmMessage()
        );
    }
}
