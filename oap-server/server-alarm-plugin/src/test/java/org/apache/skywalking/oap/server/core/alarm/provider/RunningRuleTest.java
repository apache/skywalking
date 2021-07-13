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

import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MultiIntValuesHolder;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

/**
 * Running rule is the core of how does alarm work.
 * <p>
 * So in this test, we need to simulate a lot of scenario to see the reactions.
 */
public class RunningRuleTest {
    private static DateTimeFormatter TIME_BUCKET_FORMATTER = DateTimeFormat.forPattern("yyyyMMddHHmm");

    @Test
    public void testInitAndStart() {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setMetricsName("endpoint_percent");
        alarmRule.setOp("<");
        alarmRule.setThreshold("75");
        alarmRule.setCount(3);
        alarmRule.setPeriod(15);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);
        LocalDateTime startTime = TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301434");
        long timeInPeriod1 = 201808301434L;
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));

        Map<MetaInAlarm, RunningRule.Window> windows = Whitebox.getInternalState(runningRule, "windows");

        RunningRule.Window window = windows.get(getMetaInAlarm(123));
        LocalDateTime endTime = Whitebox.getInternalState(window, "endTime");
        int period = Whitebox.getInternalState(window, "period");
        LinkedList<Metrics> metricsBuffer = Whitebox.getInternalState(window, "values");

        Assert.assertTrue(startTime.equals(endTime));
        Assert.assertEquals(15, period);
        Assert.assertEquals(15, metricsBuffer.size());
    }

    @Test
    public void testAlarm() {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setMetricsName("endpoint_percent");
        alarmRule.setOp("<");
        alarmRule.setThreshold("75");
        alarmRule.setCount(3);
        alarmRule.setPeriod(15);
        alarmRule.setMessage("Successful rate of endpoint {name} is lower than 75%");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);
        LocalDateTime startTime = TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301440");

        long timeInPeriod1 = 201808301434L;
        long timeInPeriod2 = 201808301436L;
        long timeInPeriod3 = 201808301438L;

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));

        // check at 201808301440
        List<AlarmMessage> alarmMessages = runningRule.check();
        Assert.assertEquals(0, alarmMessages.size());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));

        // check at 201808301440
        alarmMessages = runningRule.check();
        Assert.assertEquals(1, alarmMessages.size());
    }

    @Test
    public void testMultipleValuesAlarm() {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_multiple_values_rule");
        alarmRule.setMetricsName("endpoint_percent");
        alarmRule.setOp(">");
        alarmRule.setThreshold("50,60,70,-, 100");
        alarmRule.setCount(3);
        alarmRule.setPeriod(15);
        alarmRule.setMessage("response percentile of endpoint {name} is lower than expected values");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);
        LocalDateTime startTime = TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301440");

        long timeInPeriod1 = 201808301434L;
        long timeInPeriod2 = 201808301436L;
        long timeInPeriod3 = 201808301438L;

        runningRule.in(getMetaInAlarm(123), getMultipleValueMetrics(timeInPeriod1, 70, 60, 40, 40, 40));
        runningRule.in(getMetaInAlarm(123), getMultipleValueMetrics(timeInPeriod2, 60, 60, 40, 40, 40));

        // check at 201808301440
        List<AlarmMessage> alarmMessages = runningRule.check();
        Assert.assertEquals(0, alarmMessages.size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441"));

        runningRule.in(getMetaInAlarm(123), getMultipleValueMetrics(timeInPeriod3, 74, 60, 40, 40, 40));

        // check at 201808301440
        alarmMessages = runningRule.check();
        Assert.assertEquals(1, alarmMessages.size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441"));
    }

    @Test
    public void testLabeledAlarm() {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setIncludeLabels(Lists.newArrayList("95", "99"));
        assertLabeled(alarmRule);
        alarmRule = new AlarmRule();
        alarmRule.setIncludeLabelsRegex("9\\d{1}");
        assertLabeled(alarmRule);
        alarmRule = new AlarmRule();
        alarmRule.setExcludeLabels(Lists.newArrayList("50", "75"));
        assertLabeled(alarmRule);
        alarmRule = new AlarmRule();
        alarmRule.setExcludeLabelsRegex("^[5-7][0-9]$");
        assertLabeled(alarmRule);
    }

    @Test
    public void testNoAlarm() {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setMetricsName("endpoint_percent");
        alarmRule.setOp(">");
        alarmRule.setThreshold("75");
        alarmRule.setCount(3);
        alarmRule.setPeriod(15);
        //alarmRule.setSilencePeriod(0);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);
        LocalDateTime startTime = TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441");

        final boolean[] isAlarm = {false};
        AlarmCallback assertCallback = new AlarmCallback() {
            @Override
            public void doAlarm(List<AlarmMessage> alarmMessage) {
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
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod4, 90));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod5, 95));

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
        alarmRule.setMetricsName("endpoint_percent");
        alarmRule.setOp("<");
        alarmRule.setThreshold("75");
        alarmRule.setCount(3);
        alarmRule.setPeriod(15);
        alarmRule.setSilencePeriod(2);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        long timeInPeriod1 = 201808301434L;
        long timeInPeriod2 = 201808301436L;
        long timeInPeriod3 = 201808301438L;
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));

        // check at 201808301440
        Assert.assertEquals(0, runningRule.check().size()); //check matches, no alarm
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441"));

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));

        // check at 201808301440
        Assert.assertEquals(1, runningRule.check().size()); //alarm
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441"));

        // check at 201808301442
        Assert.assertEquals(0, runningRule.check().size()); //silence, no alarm
        Assert.assertEquals(0, runningRule.check().size()); //silence, no alarm
        Assert.assertNotEquals(0, runningRule.check().size()); //alarm
        Assert.assertEquals(0, runningRule.check().size()); //silence, no alarm
        Assert.assertEquals(0, runningRule.check().size()); //silence, no alarm
        Assert.assertNotEquals(0, runningRule.check().size()); //alarm
    }

    @Test
    public void testExclude() {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setMetricsName("endpoint_percent");
        alarmRule.setOp("<");
        alarmRule.setThreshold("75");
        alarmRule.setCount(3);
        alarmRule.setPeriod(15);
        alarmRule.setMessage("Successful rate of endpoint {name} is lower than 75%");
        alarmRule.setExcludeNames(Lists.newArrayList("Service_123"));
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        long timeInPeriod1 = 201808301434L;
        long timeInPeriod2 = 201808301436L;
        long timeInPeriod3 = 201808301438L;

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));

        // check at 201808301440
        Assert.assertEquals(0, runningRule.check().size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441"));
        // check at 201808301441
        Assert.assertEquals(0, runningRule.check().size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301442"));
        // check at 201808301442
        Assert.assertEquals(0, runningRule.check().size());
    }

    @Test
    public void testIncludeNamesRegex() {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setMetricsName("endpoint_percent");
        alarmRule.setOp("<");
        alarmRule.setThreshold("1000");
        alarmRule.setCount(1);
        alarmRule.setPeriod(10);
        alarmRule.setMessage(
            "Response time of service instance {name} is more than 1000ms in 2 minutes of last 10 minutes");
        alarmRule.setIncludeNamesRegex("Service\\_1(\\d)+");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        long timeInPeriod1 = 201808301434L;
        long timeInPeriod2 = 201808301436L;
        long timeInPeriod3 = 201808301439L;

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 70));
        runningRule.in(getMetaInAlarm(223), getMetrics(timeInPeriod3, 74));

        // check at 201808301440
        Assert.assertEquals(1, runningRule.check().size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441"));
        // check at 201808301441
        Assert.assertEquals(1, runningRule.check().size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301446"));
        // check at 201808301442
        Assert.assertEquals(0, runningRule.check().size());
    }

    @Test
    public void testExcludeNamesRegex() {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setMetricsName("endpoint_percent");
        alarmRule.setOp("<");
        alarmRule.setThreshold("1000");
        alarmRule.setCount(1);
        alarmRule.setPeriod(10);
        alarmRule.setMessage(
            "Response time of service instance {name} is more than 1000ms in 2 minutes of last 10 minutes");
        alarmRule.setExcludeNamesRegex("Service\\_2(\\d)+");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        long timeInPeriod1 = 201808301434L;
        long timeInPeriod2 = 201808301436L;
        long timeInPeriod3 = 201808301439L;

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 70));
        runningRule.in(getMetaInAlarm(223), getMetrics(timeInPeriod3, 74));

        // check at 201808301440
        Assert.assertEquals(1, runningRule.check().size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441"));
        // check at 201808301441
        Assert.assertEquals(1, runningRule.check().size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301446"));
        // check at 201808301442
        Assert.assertEquals(0, runningRule.check().size());
    }

    private MetaInAlarm getMetaInAlarm(int id) {
        return new MetaInAlarm() {
            @Override
            public String getScope() {
                return "SERVICE";
            }

            @Override
            public int getScopeId() {
                return DefaultScopeDefine.SERVICE;
            }

            @Override
            public String getName() {
                return "Service_" + id;
            }

            @Override
            public String getMetricsName() {
                return "endpoint_percent";
            }

            @Override
            public String getId0() {
                return "" + id;
            }

            @Override
            public String getId1() {
                return Const.EMPTY_STRING;
            }

            @Override
            public boolean equals(Object o) {
                MetaInAlarm target = (MetaInAlarm) o;
                return (id + "").equals(target.getId0());
            }

            @Override
            public int hashCode() {
                return Objects.hash(id);
            }
        };
    }

    private Metrics getMetrics(long timeBucket, int value) {
        MockMetrics mockMetrics = new MockMetrics();
        mockMetrics.setValue(value);
        mockMetrics.setTimeBucket(timeBucket);
        return mockMetrics;
    }

    private Metrics getMultipleValueMetrics(long timeBucket, int... values) {
        MockMultipleValueMetrics mockMultipleValueMetrics = new MockMultipleValueMetrics();
        mockMultipleValueMetrics.setValues(values);
        mockMultipleValueMetrics.setTimeBucket(timeBucket);
        return mockMultipleValueMetrics;

    }

    private Metrics getLabeledValueMetrics(long timeBucket, String values) {
        MockLabeledValueMetrics mockLabeledValueMetrics = new MockLabeledValueMetrics();
        mockLabeledValueMetrics.setValue(new DataTable(values));
        mockLabeledValueMetrics.setTimeBucket(timeBucket);
        return mockLabeledValueMetrics;
    }

    private class MockMetrics extends Metrics implements IntValueHolder {
        private int value;

        @Override
        protected String id0() {
            return null;
        }

        @Override
        public boolean combine(Metrics metrics) {
            return true;
        }

        @Override
        public void calculate() {

        }

        @Override
        public Metrics toHour() {
            return null;
        }

        @Override
        public Metrics toDay() {
            return null;
        }

        @Override
        public int getValue() {
            return value;
        }

        @Override
        public void deserialize(RemoteData remoteData) {

        }

        @Override
        public RemoteData.Builder serialize() {
            return null;
        }

        public void setValue(int value) {
            this.value = value;
        }

        @Override
        public int remoteHashCode() {
            return 0;
        }
    }

    private class MockMultipleValueMetrics extends Metrics implements MultiIntValuesHolder {
        private int[] values;

        public void setValues(int[] values) {
            this.values = values;
        }

        @Override
        protected String id0() {
            return null;
        }

        @Override
        public boolean combine(Metrics metrics) {
            return true;
        }

        @Override
        public void calculate() {

        }

        @Override
        public Metrics toHour() {
            return null;
        }

        @Override
        public Metrics toDay() {
            return null;
        }

        @Override
        public int[] getValues() {
            return values;
        }

        @Override
        public int remoteHashCode() {
            return 0;
        }

        @Override
        public void deserialize(RemoteData remoteData) {

        }

        @Override
        public RemoteData.Builder serialize() {
            return null;
        }
    }

    private class MockLabeledValueMetrics extends Metrics implements LabeledValueHolder {

        @Getter
        @Setter
        private DataTable value;

        @Override
        protected String id0() {
            return null;
        }

        @Override
        public boolean combine(Metrics metrics) {
            return true;
        }

        @Override
        public void calculate() {

        }

        @Override
        public Metrics toHour() {
            return null;
        }

        @Override
        public Metrics toDay() {
            return null;
        }

        @Override
        public int remoteHashCode() {
            return 0;
        }

        @Override
        public void deserialize(RemoteData remoteData) {

        }

        @Override
        public RemoteData.Builder serialize() {
            return null;
        }
    }

    private void assertLabeled(AlarmRule alarmRule) {
        alarmRule.setAlarmRuleName("endpoint_percent_alarm_rule");
        alarmRule.setMetricsName("endpoint_percent");
        alarmRule.setOp(">");
        alarmRule.setThreshold("10");
        alarmRule.setCount(3);
        alarmRule.setPeriod(15);
        alarmRule.setMessage("response percentile of endpoint {name} is lower than expected value");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);
        LocalDateTime startTime = TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301440");

        long timeInPeriod1 = 201808301434L;
        long timeInPeriod2 = 201808301436L;
        long timeInPeriod3 = 201808301438L;

        runningRule.in(getMetaInAlarm(123), getLabeledValueMetrics(timeInPeriod1, "50,17|99,11"));
        runningRule.in(getMetaInAlarm(123), getLabeledValueMetrics(timeInPeriod2, "75,15|95,12"));

        List<AlarmMessage> alarmMessages = runningRule.check();
        Assert.assertEquals(0, alarmMessages.size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441"));

        runningRule.in(getMetaInAlarm(123), getLabeledValueMetrics(timeInPeriod3, "90,1|99,20"));

        // check at 201808301440
        alarmMessages = runningRule.check();
        Assert.assertEquals(1, alarmMessages.size());
        runningRule.moveTo(TIME_BUCKET_FORMATTER.parseLocalDateTime("201808301441"));
    }
}
