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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.indicator.IntValueHolder;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

/**
 * Running rule is the core of how does alarm work.
 *
 * So in this test, we need to simulate a lot of scenario to see the reactions.
 *
 * @author wusheng
 */
public class RunningRuleTest {
    private static DateTimeFormatter TIME_BUCKET_FORMATTER = DateTimeFormat.forPattern("yyyyMMddHHmm");

    @Test
    public void testInitAndStart() {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setIndicatorName("endpoint_percent");
        alarmRule.setOp("<");
        alarmRule.setThreshold("75");
        alarmRule.setCount(3);
        alarmRule.setPeriod(15);

        RunningRule runningRule = new RunningRule(alarmRule);
        LocalDateTime startTime = TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301434");
        long timeInPeriod1 = 201808301434L;
        runningRule.in(getMetaInAlarm(123), getIndicator(timeInPeriod1, 70));

        Map<MetaInAlarm, RunningRule.Window> windows = Whitebox.getInternalState(runningRule, "windows");

        RunningRule.Window window = windows.get(getMetaInAlarm(123));
        LocalDateTime endTime = Whitebox.getInternalState(window, "endTime");
        int period = Whitebox.getInternalState(window, "period");
        LinkedList<Indicator> indicatorBuffer = Whitebox.getInternalState(window, "values");

        Assert.assertTrue(startTime.equals(endTime));
        Assert.assertEquals(15, period);
        Assert.assertEquals(15, indicatorBuffer.size());
    }

    @Test
    public void testAlarm() {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setIndicatorName("endpoint_percent");
        alarmRule.setOp("<");
        alarmRule.setThreshold("75");
        alarmRule.setCount(3);
        alarmRule.setPeriod(15);
        alarmRule.setMessage("Successful rate of endpoint {name} is lower than 75%");

        RunningRule runningRule = new RunningRule(alarmRule);
        LocalDateTime startTime = TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301440");

        long timeInPeriod1 = 201808301434L;
        long timeInPeriod2 = 201808301436L;
        long timeInPeriod3 = 201808301438L;

        runningRule.in(getMetaInAlarm(123), getIndicator(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getIndicator(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123), getIndicator(timeInPeriod3, 74));

        // check at 201808301440
        List<AlarmMessage> alarmMessages = runningRule.check();
        Assert.assertEquals(0, alarmMessages.size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441"));
        // check at 201808301441
        alarmMessages = runningRule.check();
        Assert.assertEquals(0, alarmMessages.size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301442"));
        // check at 201808301442
        alarmMessages = runningRule.check();
        Assert.assertEquals(1, alarmMessages.size());
        Assert.assertEquals("Successful rate of endpoint Service_123 is lower than 75%", alarmMessages.get(0).getAlarmMessage());
    }

    @Test
    public void testNoAlarm() {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setIndicatorName("endpoint_percent");
        alarmRule.setOp(">");
        alarmRule.setThreshold("75");
        alarmRule.setCount(3);
        alarmRule.setPeriod(15);
        //alarmRule.setSilencePeriod(0);

        RunningRule runningRule = new RunningRule(alarmRule);
        LocalDateTime startTime = TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441");

        final boolean[] isAlarm = {false};
        AlarmCallback assertCallback = new AlarmCallback() {
            @Override public void doAlarm(List<AlarmMessage> alarmMessage) {
                isAlarm[0] = true;
            }
        };
        LinkedList<AlarmCallback> callbackList = new LinkedList<>();
        callbackList.add(assertCallback);

        long timeInPeriod1 = 201808301434L;
        long timeInPeriod2 = 201808301436L;
        long timeInPeriod3 = 201808301438L;
        long timeInPeriod4 = 201808301432L;
        long timeInPeriod5 = 201808301440L;
        runningRule.in(getMetaInAlarm(123), getIndicator(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getIndicator(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123), getIndicator(timeInPeriod3, 74));
        runningRule.in(getMetaInAlarm(123), getIndicator(timeInPeriod4, 90));
        runningRule.in(getMetaInAlarm(123), getIndicator(timeInPeriod5, 95));

        // check at 201808301440
        Assert.assertEquals(0, runningRule.check().size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301442"));
        // check at 201808301441
        Assert.assertEquals(0, runningRule.check().size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301443"));
        // check at 201808301442
        Assert.assertEquals(0, runningRule.check().size());
    }

    @Test
    public void testSilence() {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setIndicatorName("endpoint_percent");
        alarmRule.setOp("<");
        alarmRule.setThreshold("75");
        alarmRule.setCount(3);
        alarmRule.setPeriod(15);
        alarmRule.setSilencePeriod(2);

        RunningRule runningRule = new RunningRule(alarmRule);

        long timeInPeriod1 = 201808301434L;
        long timeInPeriod2 = 201808301436L;
        long timeInPeriod3 = 201808301438L;
        runningRule.in(getMetaInAlarm(123), getIndicator(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getIndicator(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123), getIndicator(timeInPeriod3, 74));

        // check at 201808301440
        Assert.assertEquals(0, runningRule.check().size()); //check matches, no alarm
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441"));
        // check at 201808301441
        Assert.assertEquals(0,  runningRule.check().size()); //check matches, no alarm
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301442"));
        // check at 201808301442
        Assert.assertNotEquals(0, runningRule.check().size()); //alarm
        Assert.assertEquals(0, runningRule.check().size()); //silence, no alarm
        Assert.assertEquals(0, runningRule.check().size()); //silence, no alarm
        Assert.assertNotEquals(0, runningRule.check().size()); //alarm
        Assert.assertEquals(0, runningRule.check().size()); //silence, no alarm
        Assert.assertEquals(0, runningRule.check().size()); //silence, no alarm
        Assert.assertNotEquals(0, runningRule.check().size()); //alarm
    }

    private MetaInAlarm getMetaInAlarm(int id) {
        return new MetaInAlarm() {
            @Override public Scope getScope() {
                return Scope.Service;
            }

            @Override public String getName() {
                return "Service_" + id;
            }

            @Override public String getIndicatorName() {
                return "endpoint_percent";
            }

            @Override public int getId0() {
                return id;
            }

            @Override public int getId1() {
                return 0;
            }

            @Override public boolean equals(Object o) {
                MetaInAlarm target = (MetaInAlarm)o;
                return id == target.getId0();
            }

            @Override public int hashCode() {
                return Objects.hash(id);
            }
        };
    }

    private Indicator getIndicator(long timebucket, int value) {
        MockIndicator indicator = new MockIndicator();
        indicator.setValue(value);
        indicator.setTimeBucket(timebucket);
        return indicator;
    }

    private class MockIndicator extends Indicator implements IntValueHolder {
        private int value;

        @Override public String id() {
            return null;
        }

        @Override public void combine(Indicator indicator) {

        }

        @Override public void calculate() {

        }

        @Override public Indicator toHour() {
            return null;
        }

        @Override public Indicator toDay() {
            return null;
        }

        @Override public Indicator toMonth() {
            return null;
        }

        @Override public int getValue() {
            return value;
        }

        @Override public void deserialize(RemoteData remoteData) {

        }

        @Override public RemoteData.Builder serialize() {
            return null;
        }

        public void setValue(int value) {
            this.value = value;
        }

        @Override public int remoteHashCode() {
            return 0;
        }
    }
}
