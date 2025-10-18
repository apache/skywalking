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

package org.apache.skywalking.oap.server.core.alarm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Alarm call back will be called by alarm implementor, after it decided alarm should be sent.
 */
public interface AlarmCallback {
    default Map<String, List<AlarmMessage>> groupMessagesByHook(List<AlarmMessage> alarmMessages) {
        Map<String, List<AlarmMessage>> result = new HashMap<>();
        alarmMessages.forEach(message -> {
            Set<String> hooks = message.getHooks();
            hooks.forEach(hook -> {
                List<AlarmMessage> messages = result.computeIfAbsent(hook, v -> new ArrayList<>());
                messages.add(message);
            });
        });
        return result;
    }

    void doAlarm(List<AlarmMessage> alarmMessages) throws Exception;

    void doAlarmRecovery(List<AlarmMessage> alarmRecoveryMessages) throws Exception;
}
