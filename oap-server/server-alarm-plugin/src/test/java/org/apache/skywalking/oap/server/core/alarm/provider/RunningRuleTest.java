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
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MultiIntValuesHolder;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.powermock.reflect.Whitebox;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Running rule is the core of how does alarm work.
 * <p>
 * So in this test, we need to simulate a lot of scenario to see the reactions.
 */
public class RunningRuleTest {
    @BeforeEach
    public void setup() {
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "endpoint_percent", "testColumn", Column.ValueDataType.COMMON_VALUE, 0, Scope.Endpoint.getScopeId());
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "endpoint_multiple_values", "testColumn", Column.ValueDataType.LABELED_VALUE, 0, Scope.Endpoint.getScopeId());
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "endpoint_cpm", "testColumn", Column.ValueDataType.COMMON_VALUE, 0, Scope.Endpoint.getScopeId());
    }

    @Test
    public void testInitAndStart() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("mix_rule");
        alarmRule.setExpression("sum((increase(endpoint_cpm,5) + increase(endpoint_percent,2)) > 0) >= 1");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.getIncludeMetrics().add("endpoint_cpm");
        alarmRule.setPeriod(10);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.getMillis());
        DateTime targetTime = new DateTime(TimeBucket.getTimestamp(timeInPeriod1));

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));

        Map<AlarmEntity, RunningRule.Window> windows = Whitebox.getInternalState(runningRule, "windows");

        RunningRule.Window window = windows.get(getAlarmEntity(123));
        LocalDateTime endTime = Whitebox.getInternalState(window, "endTime");
        int additionalPeriod = Whitebox.getInternalState(window, "additionalPeriod");
        LinkedList<Metrics> metricsBuffer = Whitebox.getInternalState(window, "values");

        Assertions.assertTrue(targetTime.equals(endTime.toDateTime()));
        Assertions.assertEquals(5, additionalPeriod);
        Assertions.assertEquals(15, metricsBuffer.size());
    }

    @Test
    public void testAlarm() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(15);
        alarmRule.setMessage("Successful rate of endpoint {name} is lower than 75%");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));

        // check at startTime - 4
        List<AlarmMessage> alarmMessages = runningRule.check();
        Assertions.assertEquals(0, alarmMessages.size());

        // check at startTime - 2
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));
        alarmMessages = runningRule.check();
        Assertions.assertEquals(1, alarmMessages.size());
    }

    @Test
    public void testAlarmMetricsOutOfDate() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(15);
        alarmRule.setMessage("Successful rate of endpoint {name} is lower than 75%");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(153).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(152).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(151).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        List<AlarmMessage> alarmMessages = runningRule.check();
        Assertions.assertEquals(0, alarmMessages.size());
    }

    @Test
    public void testMultipleValuesAlarm() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_multiple_values_rule");
        alarmRule.setExpression("sum(endpoint_multiple_values > 50) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_multiple_values");
        alarmRule.setPeriod(15);
        alarmRule.setMessage("response percentile of endpoint {name} is lower than expected values");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123, "endpoint_multiple_values"), getMultipleValueMetrics(timeInPeriod1, 70, 60, 40, 40, 40));
        runningRule.in(getMetaInAlarm(123, "endpoint_multiple_values"), getMultipleValueMetrics(timeInPeriod2, 60, 60, 40, 40, 40));

        // check at startTime - 4
        List<AlarmMessage> alarmMessages = runningRule.check();
        Assertions.assertEquals(0, alarmMessages.size());

        // check at now
        runningRule.moveTo(startTime.toLocalDateTime());
        runningRule.in(getMetaInAlarm(123, "endpoint_multiple_values"), getMultipleValueMetrics(timeInPeriod3, 74, 60, 40, 40, 40));
        alarmMessages = runningRule.check();
        Assertions.assertEquals(1, alarmMessages.size());
    }

    @Test
    public void testLabeledAlarm() throws IllegalExpressionException {
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "endpoint_labeled", "testColumn", Column.ValueDataType.LABELED_VALUE, 0, Scope.Endpoint.getScopeId());
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setExpression("sum(endpoint_labeled{_='95,99'} > 10) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_labeled");
        assertLabeled(alarmRule, "50,17|99,11", "75,15|95,12|99,12", "90,1|99,20", 1);
        alarmRule.setExpression("sum(endpoint_labeled > 10) >= 3");
        assertLabeled(alarmRule, "50,17|99,11", "75,15|95,12|99,12", "90,1|99,20", 1);
        alarmRule.setExpression("sum(endpoint_labeled{_='50'} > 10) >= 3");
        assertLabeled(alarmRule, "50,17|99,11", "75,15|95,12|99,12", "90,1|99,20", 0);
    }

    @Test
    public void testMultipleMetricsAlarm() throws IllegalExpressionException {
        multipleMetricsAlarm("sum((endpoint_percent < 75) * (endpoint_cpm < 100)) >= 3", 1);
    }

    @Test
    public void testMultipleMetricsNoAlarm() throws IllegalExpressionException {
        multipleMetricsAlarm("sum((endpoint_percent < 75) * (endpoint_cpm < 99)) >= 3", 0);
    }

    private void multipleMetricsAlarm(String expression, int alarmMsgSize) throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression(expression);
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.getIncludeMetrics().add("endpoint_cpm");
        alarmRule.setPeriod(15);
        alarmRule.setMessage("Successful rate of endpoint {name} is lower than 75% and cpm is lower than 100");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);
        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123, "endpoint_cpm"), getMetrics(timeInPeriod1, 50));
        runningRule.in(getMetaInAlarm(123, "endpoint_cpm"), getMetrics(timeInPeriod2, 99));

        List<AlarmMessage> alarmMessages = runningRule.check();
        Assertions.assertEquals(0, alarmMessages.size());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));
        runningRule.in(getMetaInAlarm(123, "endpoint_cpm"), getMetrics(timeInPeriod3, 60));

        alarmMessages = runningRule.check();
        Assertions.assertEquals(alarmMsgSize, alarmMessages.size());
    }

    @Test
    public void testNoAlarm() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent > 75) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(15);
        //alarmRule.setSilencePeriod(0);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        final boolean[] isAlarm = {false};
        AlarmCallback assertCallback = new AlarmCallback() {
            @Override
            public void doAlarm(List<AlarmMessage> alarmMessage) {
                isAlarm[0] = true;
            }
        };
        LinkedList<AlarmCallback> callbackList = new LinkedList<>();
        callbackList.add(assertCallback);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(7).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(5).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(3).getMillis());
        long timeInPeriod4 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(9).getMillis());
        long timeInPeriod5 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(1).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod4, 90));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod5, 95));

        // check at startTime - 1
        Assertions.assertEquals(0, runningRule.check().size());

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        Assertions.assertEquals(0, runningRule.check().size());

        // check at startTime + 1
        runningRule.moveTo(startTime.plusMinutes(1).toLocalDateTime());
        Assertions.assertEquals(0, runningRule.check().size());
    }

    @Test
    public void testSilence() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(15);
        alarmRule.setSilencePeriod(2);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));

        // check at startTime - 4
        Assertions.assertEquals(0, runningRule.check().size()); //check matches, no alarm

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));
        Assertions.assertEquals(1, runningRule.check().size()); //alarm

        // check at starTime + 1
        runningRule.moveTo(startTime.plusMinutes(1).toLocalDateTime());
        Assertions.assertEquals(0, runningRule.check().size()); //silence, no alarm
        Assertions.assertEquals(0, runningRule.check().size()); //silence, no alarm
        Assertions.assertNotEquals(0, runningRule.check().size()); //alarm
        Assertions.assertEquals(0, runningRule.check().size()); //silence, no alarm
        Assertions.assertEquals(0, runningRule.check().size()); //silence, no alarm
        Assertions.assertNotEquals(0, runningRule.check().size()); //alarm
    }

    @Test
    public void testExclude() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(15);
        alarmRule.setMessage("Successful rate of endpoint {name} is lower than 75%");
        alarmRule.setExcludeNames(Lists.newArrayList("Service_123"));
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));

        // check at startTime - 2
        Assertions.assertEquals(0, runningRule.check().size());

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        Assertions.assertEquals(0, runningRule.check().size());

        // check at startTime + 1
        runningRule.moveTo(startTime.plusMinutes(1).toLocalDateTime());
        Assertions.assertEquals(0, runningRule.check().size());
    }

    @Test
    public void testIncludeNamesRegex() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent < 1000) >= 1");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(10);
        alarmRule.setMessage(
            "Response time of service instance {name} is more than 1000ms in 2 minutes of last 10 minutes");
        alarmRule.setIncludeNamesRegex("Service\\_1(\\d)+");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(1).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 70));
        runningRule.in(getMetaInAlarm(223), getMetrics(timeInPeriod3, 74));

        // check at startTime - 1
        Assertions.assertEquals(1, runningRule.check().size());

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        Assertions.assertEquals(1, runningRule.check().size());

        // check at startTime + 6
        runningRule.moveTo(startTime.plusMinutes(6).toLocalDateTime());
        Assertions.assertEquals(0, runningRule.check().size());
    }

    @Test
    public void testExcludeNamesRegex() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent < 1000) >= 1");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(10);
        alarmRule.setMessage(
            "Response time of service instance {name} is more than 1000ms in 2 minutes of last 10 minutes");
        alarmRule.setExcludeNamesRegex("Service\\_2(\\d)+");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(1).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 70));
        runningRule.in(getMetaInAlarm(223), getMetrics(timeInPeriod3, 74));

        // check at startTime - 1
        Assertions.assertEquals(1, runningRule.check().size());

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        Assertions.assertEquals(1, runningRule.check().size());

        // check at startTime + 6
        runningRule.moveTo(startTime.plusMinutes(6).toLocalDateTime());
        Assertions.assertEquals(0, runningRule.check().size());
    }

    private MetaInAlarm getMetaInAlarm(int id) {
        return getMetaInAlarm(id, "endpoint_percent");
    }

    private MetaInAlarm getMetaInAlarm(int id, String metricName) {
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
                return metricName;
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

    private AlarmEntity getAlarmEntity(int id) {
        MetaInAlarm metaInAlarm = getMetaInAlarm(id);
        return new AlarmEntity(metaInAlarm.getScope(), metaInAlarm.getScopeId(), metaInAlarm.getName(),
                               metaInAlarm.getId0(), metaInAlarm.getId1()
        );
    }

    private class MockMetrics extends Metrics implements IntValueHolder {
        private int value;

        @Override
        protected StorageID id0() {
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
        protected StorageID id0() {
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
        protected StorageID id0() {
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

    private void assertLabeled(AlarmRule alarmRule, String value1, String value2, String value3, int alarmMsgSize) {
        alarmRule.setAlarmRuleName("endpoint_labeled_alarm_rule");
        alarmRule.setPeriod(15);
        alarmRule.setMessage("response percentile of endpoint {name} is lower than expected value");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123, "endpoint_labeled"), getLabeledValueMetrics(timeInPeriod1, value1));
        runningRule.in(getMetaInAlarm(123, "endpoint_labeled"), getLabeledValueMetrics(timeInPeriod2, value2));

        // check at startTime - 4
        List<AlarmMessage> alarmMessages = runningRule.check();
        Assertions.assertEquals(0, alarmMessages.size());

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        runningRule.in(getMetaInAlarm(123, "endpoint_labeled"), getLabeledValueMetrics(timeInPeriod3, value3));
        alarmMessages = runningRule.check();
        Assertions.assertEquals(alarmMsgSize, alarmMessages.size());
    }
}
