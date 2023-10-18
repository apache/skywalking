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

package org.apache.skywalking.oap.server.core.alarm.provider.webhook;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.HttpAlarmCallback;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

/**
 * Use SkyWalking alarm webhook API calls a remote endpoints.
 */
@Slf4j
@RequiredArgsConstructor
public class WebhookCallback extends HttpAlarmCallback {
    private final AlarmRulesWatcher alarmRulesWatcher;
    private final Gson gson = new Gson();

    @Override
    public void doAlarm(List<AlarmMessage> alarmMessages) throws Exception {
        Map<String, WebhookSettings> settingsMap = alarmRulesWatcher.getWebHooks();
        if (settingsMap == null || settingsMap.isEmpty()) {
            return;
        }

        Map<String, List<AlarmMessage>> groupedMessages = groupMessagesByHook(alarmMessages);
        for (Map.Entry<String, List<AlarmMessage>> entry : groupedMessages.entrySet()) {
            var hookName = entry.getKey();
            var messages = entry.getValue();
            var setting = settingsMap.get(hookName);
            if (setting == null || CollectionUtils.isEmpty(setting.getUrls()) || CollectionUtils.isEmpty(
                messages)) {
                continue;
            }
            for (final var url : setting.getUrls()) {
                try {
                    post(URI.create(url), gson.toJson(messages), Map.of());
                } catch (Exception e) {
                    log.error("Failed to send alarm message to Webhook: {}", url, e);
                }
            }
        }
    }
}
