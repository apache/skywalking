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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.joda.time.LocalDateTime;
import org.joda.time.Minutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alarm core includes metric values in certain time windows based on alarm settings. By using its internal timer
 * trigger and the alarm rules to decides whether send the alarm to database and webhook(s)
 *
 * @author wusheng
 */
public class AlarmCore {
    private static final Logger logger = LoggerFactory.getLogger(AlarmCore.class);

    private Map<String, List<RunningRule>> runningContext;
    private LocalDateTime lastExecuteTime;

    AlarmCore(Rules rules) {
        runningContext = new HashMap<>();
        rules.getRules().forEach(rule -> {
            RunningRule runningRule = new RunningRule(rule);

            String indicatorName = rule.getIndicatorName();

            List<RunningRule> runningRules = runningContext.get(indicatorName);
            if (runningRules == null) {
                runningRules = new ArrayList<>();
                runningContext.put(indicatorName, runningRules);
            }
            runningRules.add(runningRule);
        });
    }

    public List<RunningRule> findRunningRule(String indicatorName) {
        return runningContext.get(indicatorName);
    }

    public void start(List<AlarmCallback> allCallbacks) {
        LocalDateTime now = LocalDateTime.now();
        lastExecuteTime = now;
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                List<AlarmMessage> alarmMessageList = new ArrayList<>(30);
                LocalDateTime checkTime = LocalDateTime.now();
                int minutes = Minutes.minutesBetween(lastExecuteTime, checkTime).getMinutes();
                boolean[] hasExecute = new boolean[] {false};
                runningContext.values().forEach(ruleList -> ruleList.forEach(runningRule -> {
                    if (minutes > 0) {
                        runningRule.moveTo(checkTime);
                        /**
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
                    lastExecuteTime = checkTime.minusSeconds(checkTime.getSecondOfMinute());
                }

                if (alarmMessageList.size() > 0) {
                    allCallbacks.forEach(callback -> callback.doAlarm(alarmMessageList));
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
}
