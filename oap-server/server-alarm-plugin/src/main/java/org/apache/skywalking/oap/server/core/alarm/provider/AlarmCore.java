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

package org.apache.skywalking.oap.server.core.alarm.provider;

import java.util.Map;
import java.util.Set;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.joda.time.LocalDateTime;
import org.joda.time.Minutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Alarm core includes metrics values in certain time windows based on alarm settings. By using its internal timer
 * trigger and the alarm rules to decide whether send the alarm to database and webhook(s)
 */
public class AlarmCore {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmCore.class);

    private LocalDateTime lastExecuteTime;
    private AlarmRulesWatcher alarmRulesWatcher;

    AlarmCore(AlarmRulesWatcher alarmRulesWatcher) {
        this.alarmRulesWatcher = alarmRulesWatcher;
    }

    /**
     * Find the running rules by the metrics name.
     *
     * @param metricsName to be found
     * @return the matched running rule list, or null if not found.
     */
    public List<RunningRule> findRunningRule(String metricsName) {
        List<RunningRule> runningRules = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : alarmRulesWatcher.getExprMetricsMap().entrySet()) {
            if (entry.getValue().contains(metricsName)) {
                List<RunningRule> found = alarmRulesWatcher.getRunningContext().get(entry.getKey());
                if (found != null) {
                    runningRules.addAll(found);
                }
            }
        }
        return runningRules.size() > 0 ? runningRules : null;
    }

    public void start(List<AlarmCallback> allCallbacks) {
        LocalDateTime now = LocalDateTime.now();
        lastExecuteTime = now;
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                final List<AlarmMessage> alarmMessageList = new ArrayList<>(30);
                LocalDateTime checkTime = LocalDateTime.now();
                int minutes = Minutes.minutesBetween(lastExecuteTime, checkTime).getMinutes();
                boolean[] hasExecute = new boolean[]{false};
                alarmRulesWatcher.getRunningContext().values().forEach(ruleList -> ruleList.forEach(runningRule -> {
                    if (minutes > 0) {
                        runningRule.moveTo(checkTime);
                        /*
                         * Don't run in the first quarter per min, avoid to trigger false alarm.
                         */
                        if (checkTime.getSecondOfMinute() > 15) {
                            hasExecute[0] = true;
                            alarmMessageList.addAll(runningRule.check());
                        }
                    }
                }));
                // Set the last execute time, and make sure the second is `00`, such as: 18:30:00
                if (hasExecute[0]) {
                    lastExecuteTime = checkTime.withSecondOfMinute(0).withMillisOfSecond(0);
                }

                if (!alarmMessageList.isEmpty()) {
                    for (AlarmCallback callback : allCallbacks) {
                        callback.doAlarm(alarmMessageList);
                    }
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
}
