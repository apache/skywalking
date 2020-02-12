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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.joda.time.LocalDateTime;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.reflect.Whitebox;

/**
 * Alarm core is the trigger, which should run once per minute, also run after the first quarter in one single minute.
 */
public class AlarmCoreTest {
    /**
     * This case will cost several minutes, which causes CI very slow, so it only runs when -DAlarmCoreTest=true
     * existed.
     */
    @Test
    public void testTriggerTimePoint() throws InterruptedException {
        String test = System.getProperty("AlarmCoreTest");
        if (test == null) {
            return;
        }

        Rules emptyRules = new Rules();
        emptyRules.setRules(new ArrayList<>(0));
        emptyRules.setWebhooks(new ArrayList<>(0));
        AlarmCore core = new AlarmCore(new AlarmRulesWatcher(emptyRules, null));

        Map<String, List<RunningRule>> runningContext = Whitebox.getInternalState(core, "runningContext");

        List<RunningRule> rules = new ArrayList<>(1);
        RunningRule mockRule = PowerMockito.mock(RunningRule.class);

        List<LocalDateTime> checkTime = new LinkedList<>();
        final boolean[] isAdd = {true};

        PowerMockito.doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock mock) throws Throwable {
                if (isAdd[0]) {
                    checkTime.add(LocalDateTime.now());
                }
                return new ArrayList<>(0);
            }
        }).when(mockRule).check();

        rules.add(mockRule);
        runningContext.put("mock", rules);

        core.start(new ArrayList<>(0));

        for (int i = 0; i < 10; i++) {
            Thread.sleep(60 * 1000L);
            if (checkTime.size() >= 3) {
                isAdd[0] = false;
                Assert.assertTrue(checkTimePoints(checkTime));
                break;
            }
            if (i == 9) {
                Assert.assertTrue(false);
            }
        }
    }

    private boolean checkTimePoints(List<LocalDateTime> checkTime) {
        LocalDateTime last = null;
        for (LocalDateTime time : checkTime) {
            if (time.getSecondOfMinute() <= 15) {
                return false;
            }
            if (last != null) {
                int lastMinuteOfHour = last.getMinuteOfHour();
                int minuteOfHour = time.getMinuteOfHour();
                if (!((minuteOfHour - lastMinuteOfHour == 1) || (minuteOfHour == 0 && lastMinuteOfHour == 59))) {
                    return false;
                }
            }
            last = time;
        }
        return true;
    }
}
