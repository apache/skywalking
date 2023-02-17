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

/**
 * Use SkyWalking alarm slack webhook API calls a remote endpoints.
 */
@Slf4j
@RequiredArgsConstructor
public class SlackhookCallback extends HttpAlarmCallback {
    private static final Gson GSON = new Gson();

    private final AlarmRulesWatcher alarmRulesWatcher;

    @Override
    public void doAlarm(List<AlarmMessage> alarmMessages) throws Exception {
        if (alarmRulesWatcher.getSlackSettings() == null || alarmRulesWatcher.getSlackSettings().getWebhooks().isEmpty()) {
            return;
        }

        for (final var url : alarmRulesWatcher.getSlackSettings().getWebhooks()) {
            final var jsonObject = new JsonObject();
            final var jsonElements = new JsonArray();
            for (AlarmMessage item : alarmMessages) {
                jsonElements.add(GSON.fromJson(
                        String.format(
                                alarmRulesWatcher.getSlackSettings().getTextTemplate(), item.getAlarmMessage()
                        ), JsonObject.class));
            }
            jsonObject.add("blocks", jsonElements);
            final var body = GSON.toJson(jsonObject);
            post(URI.create(url), body, Map.of());
        }
    }
}
