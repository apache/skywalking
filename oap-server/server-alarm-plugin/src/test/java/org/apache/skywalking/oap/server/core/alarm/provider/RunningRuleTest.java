/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License");you may not use this file except in compliance with
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

import static org.apache.skywalking.oap.server.core.alarm.provider.AlarmCore.getAlarmFiringMessageList;
import static org.apache.skywalking.oap.server.core.alarm.provider.AlarmCore.getAlarmRecoveryMessageList;

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
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("mix_rule");
        alarmRule.setExpression("sum((increase(endpoint_cpm,5) + increase(endpoint_percent,2)) > 0) >= 1");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.getIncludeMetrics().add("endpoint_cpm");
        alarmRule.setPeriod(10);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule, null);

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
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(15);
        alarmRule.setMessage("Successful rate of endpoint {name} is lower than 75%");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));

        // check at startTime - 4
        List<AlarmMessage> alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(0, alarmMessages.size());

        // check at startTime - 2
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));
        alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(1, alarmMessages.size());
    }

    @Test
    public void testAlarmMetricsOutOfDate() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(15);
        alarmRule.setMessage("Successful rate of endpoint {name} is lower than 75%");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(153).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(152).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(151).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        List<AlarmMessage> alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(0, alarmMessages.size());
    }

    @Test
    public void testLabeledAlarm() throws IllegalExpressionException {
        ValueColumnMetadata.INSTANCE.putIfAbsent(
                "endpoint_labeled", "testColumn", Column.ValueDataType.LABELED_VALUE, 0, Scope.Endpoint.getScopeId());
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setExpression("sum(endpoint_labeled{p='95,99'} > 10) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_labeled");
        assertLabeled(alarmRule, "{p=50},17|{p=99},11", "{p=75},15|{p=95},12|{p=99},12", "{p=90},1|{p=99},20", 1);
        alarmRule.setExpression("sum(endpoint_labeled > 10) >= 3");
        assertLabeled(alarmRule, "{p=50},17|{p=99},11", "{p=75},15|{p=95},12|{p=99},12", "{p=90},1|{p=99},20", 1);
        alarmRule.setExpression("sum(endpoint_labeled{_='50'} > 10) >= 3");
        assertLabeled(alarmRule, "{p=50},17|{p=99},11", "{p=75},15|{p=95},12|{p=99},12", "{p=90},1|{p=99},20", 0);
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
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression(expression);
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.getIncludeMetrics().add("endpoint_cpm");
        alarmRule.setPeriod(15);
        alarmRule.setMessage("Successful rate of endpoint {name} is lower than 75% and cpm is lower than 100");
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule, null);
        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123, "endpoint_cpm"), getMetrics(timeInPeriod1, 50));
        runningRule.in(getMetaInAlarm(123, "endpoint_cpm"), getMetrics(timeInPeriod2, 99));

        List<AlarmMessage> alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(0, alarmMessages.size());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));
        runningRule.in(getMetaInAlarm(123, "endpoint_cpm"), getMetrics(timeInPeriod3, 60));

        alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(alarmMsgSize, alarmMessages.size());
    }

    @Test
    public void testNoAlarm() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent > 75) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(15);
        //alarmRule.setSilencePeriod(0);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule, null);

        final boolean[] isAlarm = {false};
        AlarmCallback assertCallback = new AlarmCallback() {
            @Override
            public void doAlarm(List<AlarmMessage> alarmMessage) {
                isAlarm[0] = true;
            }

            @Override
            public void doAlarmRecovery(List<AlarmMessage> alarmResolvedMessages) {
                isAlarm[0] = false;
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
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size());

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size());

        // check at startTime + 1
        runningRule.moveTo(startTime.plusMinutes(1).toLocalDateTime());
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size());
    }

    @Test
    public void testSilence() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(15);
        alarmRule.setSilencePeriod(2);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));

        // check at startTime - 4
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size()); //check matches, no alarm

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));
        Assertions.assertEquals(1, getAlarmFiringMessageList(runningRule.check()).size()); //alarm

        // check at starTime + 1
        runningRule.moveTo(startTime.plusMinutes(1).toLocalDateTime());
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size()); //silence, no alarm
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size()); //silence, no alarm
        Assertions.assertNotEquals(0, getAlarmFiringMessageList(runningRule.check()).size()); //alarm
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size()); //silence, no alarm
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size()); //silence, no alarm
        Assertions.assertNotEquals(0, getAlarmFiringMessageList(runningRule.check()).size()); //alarm
    }

    @Test
    public void testRecoverObservation() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(15);
        alarmRule.setRecoveryObservationPeriod(2);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));
        runningRule.moveTo(startTime.toLocalDateTime());
        Assertions.assertEquals(1, getAlarmFiringMessageList(runningRule.check()).size()); //alarm
        runningRule.moveTo(startTime.plusMinutes(8).toLocalDateTime());
        Assertions.assertEquals(0, getAlarmRecoveryMessageList(runningRule.check()).size()); //no recovery
        runningRule.moveTo(startTime.plusMinutes(9).toLocalDateTime());
        Assertions.assertEquals(0, getAlarmRecoveryMessageList(runningRule.check()).size()); //recoverObserving
        Assertions.assertEquals(0, getAlarmRecoveryMessageList(runningRule.check()).size()); //recoverObserving
        Assertions.assertEquals(1, getAlarmRecoveryMessageList(runningRule.check()).size()); //recovered
    }

    @Test
    public void testRecover() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(15);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));
        runningRule.moveTo(startTime.toLocalDateTime());
        Assertions.assertEquals(1, getAlarmFiringMessageList(runningRule.check()).size()); //alarm
        runningRule.moveTo(startTime.plusMinutes(9).toLocalDateTime());
        Assertions.assertEquals(1, getAlarmRecoveryMessageList(runningRule.check()).size()); //recovery
    }

    @Test
    public void testExclude() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("endpoint_percent_rule");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 3");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(15);
        alarmRule.setMessage("Successful rate of endpoint {name} is lower than 75%");
        alarmRule.setExcludeNames(Lists.newArrayList("Service_123"));
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 71));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod3, 74));

        // check at startTime - 2
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size());

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size());

        // check at startTime + 1
        runningRule.moveTo(startTime.plusMinutes(1).toLocalDateTime());
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size());
    }

    @Test
    public void testIncludeNamesRegex() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
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
        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(1).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 70));
        runningRule.in(getMetaInAlarm(223), getMetrics(timeInPeriod3, 74));

        // check at startTime - 1
        Assertions.assertEquals(1, getAlarmFiringMessageList(runningRule.check()).size());

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        Assertions.assertEquals(1, getAlarmFiringMessageList(runningRule.check()).size());

        // check at startTime + 6
        runningRule.moveTo(startTime.plusMinutes(6).toLocalDateTime());
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size());
    }

    @Test
    public void testExcludeNamesRegex() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
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
        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(1).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeInPeriod2, 70));
        runningRule.in(getMetaInAlarm(223), getMetrics(timeInPeriod3, 74));

        // check at startTime - 1
        Assertions.assertEquals(1, getAlarmFiringMessageList(runningRule.check()).size());

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        Assertions.assertEquals(1, getAlarmFiringMessageList(runningRule.check()).size());

        // check at startTime + 6
        runningRule.moveTo(startTime.plusMinutes(6).toLocalDateTime());
        Assertions.assertEquals(0, getAlarmFiringMessageList(runningRule.check()).size());
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
        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();
        long timeInPeriod1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(6).getMillis());
        long timeInPeriod2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(4).getMillis());
        long timeInPeriod3 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());

        runningRule.in(getMetaInAlarm(123, "endpoint_labeled"), getLabeledValueMetrics(timeInPeriod1, value1));
        runningRule.in(getMetaInAlarm(123, "endpoint_labeled"), getLabeledValueMetrics(timeInPeriod2, value2));

        // check at startTime - 4
        List<AlarmMessage> alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(0, alarmMessages.size());

        // check at startTime
        runningRule.moveTo(startTime.toLocalDateTime());
        runningRule.in(getMetaInAlarm(123, "endpoint_labeled"), getLabeledValueMetrics(timeInPeriod3, value3));
        alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(alarmMsgSize, alarmMessages.size());
    }

    @Test
    public void testAlarmStateMachine_NoSilenceNoRecoveryObservation() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("test_no_silence_no_recovery");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 2");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(3);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});
        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();
        long timeBucket1 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis());
        long timeBucket2 = TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(1).getMillis());

        runningRule.in(getMetaInAlarm(123), getMetrics(timeBucket1, 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(timeBucket2, 71));

        runningRule.moveTo(startTime.toLocalDateTime());
        List<AlarmMessage> alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(1, alarmMessages.size(), "Should trigger alarm");

        RunningRule.Window window = getWindow(runningRule, 123);
        RunningRule.Window.AlarmStateMachine stateMachine = window.getStateMachine();
        Assertions.assertEquals(RunningRule.State.FIRING, stateMachine.getCurrentState());

        long timeBucket3 = TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(1).getMillis());
        runningRule.in(getMetaInAlarm(123), getMetrics(timeBucket3, 80));
        runningRule.moveTo(startTime.plusMinutes(1).toLocalDateTime());

        List<AlarmMessage> recoveryMessages = getAlarmRecoveryMessageList(runningRule.check());
        Assertions.assertEquals(1, recoveryMessages.size(), "Should recover immediately");
        Assertions.assertEquals(RunningRule.State.RECOVERED, stateMachine.getCurrentState());

        long timeBucket4 = TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(2).getMillis());
        runningRule.in(getMetaInAlarm(123), getMetrics(timeBucket4, 80));
        runningRule.moveTo(startTime.plusMinutes(2).toLocalDateTime());
        List<AlarmMessage> messages = getAlarmRecoveryMessageList(runningRule.check());
        Assertions.assertEquals(0, messages.size(), "Should be empty");
        Assertions.assertEquals(RunningRule.State.NORMAL, stateMachine.getCurrentState());
    }

    @Test
    public void testAlarmStateMachine_OnlySilencePeriod() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("test_only_silence");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 1");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(3);
        alarmRule.setSilencePeriod(2);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});

        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();
        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis()), 70));

        runningRule.moveTo(startTime.toLocalDateTime());
        List<AlarmMessage> alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(1, alarmMessages.size(), "Should trigger alarm");
        RunningRule.Window window = getWindow(runningRule, 123);
        RunningRule.Window.AlarmStateMachine stateMachine = window.getStateMachine();
        Assertions.assertEquals(RunningRule.State.FIRING, stateMachine.getCurrentState());

        runningRule.moveTo(startTime.plusMinutes(1).toLocalDateTime());
        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(1).getMillis()), 72));
        alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(0, alarmMessages.size(), "Should be silenced");
        Assertions.assertEquals(RunningRule.State.SILENCED, stateMachine.getCurrentState());

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(2).getMillis()), 72));
        runningRule.moveTo(startTime.plusMinutes(2).toLocalDateTime());
        alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(0, alarmMessages.size(), "Should be silenced");
        Assertions.assertEquals(RunningRule.State.SILENCED, stateMachine.getCurrentState());

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(3).getMillis()), 80));
        runningRule.moveTo(startTime.plusMinutes(3).toLocalDateTime());
        alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(1, alarmMessages.size(), "Should trigger alarm after silence");
        Assertions.assertEquals(RunningRule.State.FIRING, stateMachine.getCurrentState());

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(4).getMillis()), 80));
        runningRule.moveTo(startTime.plusMinutes(4).toLocalDateTime());
        alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(0, alarmMessages.size(), "Should be silenced");
        Assertions.assertEquals(RunningRule.State.SILENCED, stateMachine.getCurrentState());

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(5).getMillis()), 80));
        runningRule.moveTo(startTime.plusMinutes(5).toLocalDateTime());
        alarmMessages = getAlarmRecoveryMessageList(runningRule.check());
        Assertions.assertEquals(1, alarmMessages.size(), "Should recover immediately");
        Assertions.assertEquals(RunningRule.State.RECOVERED, stateMachine.getCurrentState());

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(6).getMillis()), 80));
        runningRule.moveTo(startTime.plusMinutes(6).toLocalDateTime());
        alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(0, alarmMessages.size(), "Should be normal");
        Assertions.assertEquals(RunningRule.State.NORMAL, stateMachine.getCurrentState());
    }

    @Test
    public void testAlarmStateMachine_OnlyRecoveryObservationPeriod() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("test_only_recovery_observation");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 1");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(3);
        alarmRule.setRecoveryObservationPeriod(1);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});

        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis()), 70));
        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(1).getMillis()), 72));

        runningRule.moveTo(startTime.toLocalDateTime());
        List<AlarmMessage> alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(1, alarmMessages.size(), "Should trigger alarm");
        RunningRule.Window window = getWindow(runningRule, 123);
        RunningRule.Window.AlarmStateMachine stateMachine = window.getStateMachine();
        Assertions.assertEquals(RunningRule.State.FIRING, stateMachine.getCurrentState());

        runningRule.moveTo(startTime.plusMinutes(1).toLocalDateTime());
        alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(1, alarmMessages.size(), "Should trigger alarm");
        Assertions.assertEquals(RunningRule.State.FIRING, stateMachine.getCurrentState());

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(2).getMillis()), 80));
        runningRule.moveTo(startTime.plusMinutes(2).toLocalDateTime());
        List<AlarmMessage> recoveryMessages = getAlarmRecoveryMessageList(runningRule.check());
        Assertions.assertEquals(0, recoveryMessages.size(), "Should not recover yet");
        Assertions.assertEquals(RunningRule.State.OBSERVING_RECOVERY, stateMachine.getCurrentState());

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(2).getMillis()), 80));
        runningRule.moveTo(startTime.plusMinutes(2).toLocalDateTime());
        recoveryMessages = getAlarmRecoveryMessageList(runningRule.check());
        Assertions.assertEquals(1, recoveryMessages.size(), "Should recover after observation");
        Assertions.assertEquals(RunningRule.State.RECOVERED, stateMachine.getCurrentState());

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(3).getMillis()), 80));
        runningRule.moveTo(startTime.plusMinutes(3).toLocalDateTime());
        recoveryMessages = getAlarmRecoveryMessageList(runningRule.check());
        Assertions.assertEquals(0, recoveryMessages.size(), "Should be normal");
        Assertions.assertEquals(RunningRule.State.NORMAL, stateMachine.getCurrentState());
    }

    @Test
    public void testAlarmStateMachine_SilenceGreaterThanRecovery() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("test_silence_gt_recovery");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 1");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(5);
        alarmRule.setSilencePeriod(3);
        alarmRule.setRecoveryObservationPeriod(2);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});

        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();

        runningRule.in(getMetaInAlarm(123), getMetrics(
                TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis()), 70));
        RunningRule.Window window = getWindow(runningRule, 123);
        RunningRule.Window.AlarmStateMachine stateMachine = window.getStateMachine();

        runningRule.moveTo(startTime.toLocalDateTime());
        List<AlarmMessage> alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(1, alarmMessages.size());
        Assertions.assertEquals(RunningRule.State.FIRING, stateMachine.getCurrentState());

        for (int i = 0; i <= 3; i++) {
            runningRule.moveTo(startTime.plusMinutes(i).toLocalDateTime());
            runningRule.in(getMetaInAlarm(123), getMetrics(
                    TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(i).getMillis()), 72));

            alarmMessages = getAlarmFiringMessageList(runningRule.check());
            if (i < 3) {
                Assertions.assertEquals(0, alarmMessages.size(), "Should be silenced at minute " + i);
                Assertions.assertEquals(RunningRule.State.SILENCED, stateMachine.getCurrentState());
            } else {
                Assertions.assertEquals(1, alarmMessages.size(), "Should fire after silence period");
                Assertions.assertEquals(RunningRule.State.FIRING, stateMachine.getCurrentState());
            }
        }
        for (int i = 0; i <= 2; i++) {
            runningRule.moveTo(startTime.plusMinutes(8 + i).toLocalDateTime());
            runningRule.in(getMetaInAlarm(123), getMetrics(
                    TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(8 + i).getMillis()), 80));
            if (i < 2) {
                List<AlarmMessage> recoveryMessages = getAlarmRecoveryMessageList(runningRule.check());
                Assertions.assertEquals(0, recoveryMessages.size(), "Should not recover immediately");
                Assertions.assertEquals(RunningRule.State.OBSERVING_RECOVERY, stateMachine.getCurrentState());
            } else {
                List<AlarmMessage> recoveryMessages = getAlarmRecoveryMessageList(runningRule.check());
                Assertions.assertEquals(1, recoveryMessages.size(), "Should recover after observation period");
                Assertions.assertEquals(RunningRule.State.RECOVERED, stateMachine.getCurrentState());
            }
        }
        runningRule.moveTo(startTime.plusMinutes(11).toLocalDateTime());
        List<AlarmMessage> recoveryMessages = getAlarmRecoveryMessageList(runningRule.check());
        Assertions.assertEquals(0, recoveryMessages.size(), "Should recover after observation period");
        Assertions.assertEquals(RunningRule.State.NORMAL, stateMachine.getCurrentState());
    }

    @Test
    public void testAlarmStateMachine_RecoveryGreaterThanSilence() throws IllegalExpressionException {
        AlarmRule alarmRule = new AlarmRule(null);
        alarmRule.setAlarmRuleName("test_recovery_gt_silence");
        alarmRule.setExpression("sum(endpoint_percent < 75) >= 1");
        alarmRule.getIncludeMetrics().add("endpoint_percent");
        alarmRule.setPeriod(3);
        alarmRule.setSilencePeriod(2);
        alarmRule.setRecoveryObservationPeriod(3);
        alarmRule.setTags(new HashMap<String, String>() {{
            put("key", "value");
        }});

        RunningRule runningRule = new RunningRule(alarmRule, null);

        DateTime startTime = DateTime.now();

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.minusMinutes(2).getMillis()), 70));

        runningRule.moveTo(startTime.toLocalDateTime());
        List<AlarmMessage> alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(1, alarmMessages.size());
        RunningRule.Window window = getWindow(runningRule, 123);
        RunningRule.Window.AlarmStateMachine stateMachine = window.getStateMachine();
        Assertions.assertEquals(RunningRule.State.FIRING, stateMachine.getCurrentState());

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(1).getMillis()), 72));
        runningRule.moveTo(startTime.plusMinutes(1).toLocalDateTime());
        alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(0, alarmMessages.size(), "Should be silenced");
        Assertions.assertEquals(RunningRule.State.SILENCED, stateMachine.getCurrentState());

        runningRule.moveTo(startTime.plusMinutes(2).toLocalDateTime());
        alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(0, alarmMessages.size(), "Should be silenced");
        Assertions.assertEquals(RunningRule.State.SILENCED, stateMachine.getCurrentState());

        runningRule.moveTo(startTime.plusMinutes(3).toLocalDateTime());
        alarmMessages = getAlarmFiringMessageList(runningRule.check());
        Assertions.assertEquals(1, alarmMessages.size(), "Should fire after silence period");
        Assertions.assertEquals(RunningRule.State.FIRING, stateMachine.getCurrentState());

        runningRule.moveTo(startTime.plusMinutes(4).toLocalDateTime());
        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(4).getMillis()), 80));
        List<AlarmMessage> recoveryMessages = getAlarmRecoveryMessageList(runningRule.check());
        Assertions.assertEquals(0, recoveryMessages.size(), "Should not recover immediately");
        Assertions.assertEquals(RunningRule.State.OBSERVING_RECOVERY, stateMachine.getCurrentState());

        runningRule.moveTo(startTime.plusMinutes(5).toLocalDateTime());
        recoveryMessages = getAlarmRecoveryMessageList(runningRule.check());
        Assertions.assertEquals(0, recoveryMessages.size(), "Should still in observation");

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(6).getMillis()), 80));
        runningRule.moveTo(startTime.plusMinutes(6).toLocalDateTime());
        recoveryMessages = getAlarmRecoveryMessageList(runningRule.check());
        Assertions.assertEquals(0, recoveryMessages.size(), "Should still in observation");

        runningRule.in(getMetaInAlarm(123), getMetrics(TimeBucket.getMinuteTimeBucket(startTime.plusMinutes(7).getMillis()), 80));
        runningRule.moveTo(startTime.plusMinutes(7).toLocalDateTime());
        recoveryMessages = getAlarmRecoveryMessageList(runningRule.check());
        Assertions.assertEquals(1, recoveryMessages.size(), "Should recover after full observation period");
        Assertions.assertEquals(RunningRule.State.RECOVERED, stateMachine.getCurrentState());

        runningRule.moveTo(startTime.plusMinutes(8).toLocalDateTime());
        recoveryMessages = getAlarmRecoveryMessageList(runningRule.check());
        Assertions.assertEquals(0, recoveryMessages.size(), "Should be normal");
        Assertions.assertEquals(RunningRule.State.NORMAL, stateMachine.getCurrentState());
    }

    private RunningRule.Window getWindow(RunningRule runningRule, int entityId) {
        Map<AlarmEntity, RunningRule.Window> windows = runningRule.getWindows();
        AlarmEntity entity = getAlarmEntity(entityId);
        return windows.get(entity);
    }
}
