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
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.DoubleValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MultiIntValuesHolder;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.joda.time.LocalDateTime;
import org.joda.time.Minutes;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * RunningRule represents each rule in running status. Based on the {@link AlarmRule} definition,
 */
@Slf4j
public class RunningRule {
    private static DateTimeFormatter TIME_BUCKET_FORMATTER = DateTimeFormat.forPattern("yyyyMMddHHmm");

    private final String ruleName;
    private final int period;
    private final String metricsName;
    private final Threshold threshold;
    private final OP op;
    private final int countThreshold;
    private final int silencePeriod;
    private final Map<MetaInAlarm, Window> windows;
    private volatile MetricsValueType valueType;
    private final List<String> includeNames;
    private final List<String> excludeNames;
    private final Pattern includeNamesRegex;
    private final Pattern excludeNamesRegex;
    private final List<String> includeLabels;
    private final List<String> excludeLabels;
    private final Pattern includeLabelsRegex;
    private final Pattern excludeLabelsRegex;
    private final AlarmMessageFormatter formatter;
    private final boolean onlyAsCondition;

    public RunningRule(AlarmRule alarmRule) {
        metricsName = alarmRule.getMetricsName();
        this.ruleName = alarmRule.getAlarmRuleName();

        // Init the empty window for alarming rule.
        windows = new ConcurrentHashMap<>();

        period = alarmRule.getPeriod();

        threshold = new Threshold(alarmRule.getAlarmRuleName(), alarmRule.getThreshold());
        op = OP.get(alarmRule.getOp());

        this.countThreshold = alarmRule.getCount();
        this.silencePeriod = alarmRule.getSilencePeriod();

        this.includeNames = alarmRule.getIncludeNames();
        this.excludeNames = alarmRule.getExcludeNames();
        this.includeNamesRegex = StringUtil.isNotEmpty(alarmRule.getIncludeNamesRegex()) ?
            Pattern.compile(alarmRule.getIncludeNamesRegex()) : null;
        this.excludeNamesRegex = StringUtil.isNotEmpty(alarmRule.getExcludeNamesRegex()) ?
            Pattern.compile(alarmRule.getExcludeNamesRegex()) : null;
        this.includeLabels = alarmRule.getIncludeLabels();
        this.excludeLabels = alarmRule.getExcludeLabels();
        this.includeLabelsRegex = StringUtil.isNotEmpty(alarmRule.getIncludeLabelsRegex()) ?
            Pattern.compile(alarmRule.getIncludeLabelsRegex()) : null;
        this.excludeLabelsRegex = StringUtil.isNotEmpty(alarmRule.getExcludeLabelsRegex()) ?
            Pattern.compile(alarmRule.getExcludeLabelsRegex()) : null;
        this.formatter = new AlarmMessageFormatter(alarmRule.getMessage());
        this.onlyAsCondition = alarmRule.isOnlyAsCondition();
    }

    /**
     * Receive metrics result from persistence, after it is saved into storage. In alarm, only minute dimensionality
     * metrics are expected to process.
     *
     * @param meta    of input metrics
     * @param metrics includes the values.
     */
    public void in(MetaInAlarm meta, Metrics metrics) {
        if (!meta.getMetricsName().equals(metricsName)) {
            //Don't match rule, exit.
            if (log.isTraceEnabled()) {
                log.trace("Metric names are inconsistent, {}-{}", meta.getMetricsName(), metricsName);
            }
            return;
        }

        final String metaName = meta.getName();
        if (!validate(metaName, includeNames, excludeNames, includeNamesRegex, excludeNamesRegex)) {
            return;
        }

        if (valueType == null) {
            if (metrics instanceof LongValueHolder) {
                valueType = MetricsValueType.LONG;
                threshold.setType(MetricsValueType.LONG);
            } else if (metrics instanceof IntValueHolder) {
                valueType = MetricsValueType.INT;
                threshold.setType(MetricsValueType.INT);
            } else if (metrics instanceof DoubleValueHolder) {
                valueType = MetricsValueType.DOUBLE;
                threshold.setType(MetricsValueType.DOUBLE);
            } else if (metrics instanceof MultiIntValuesHolder) {
                valueType = MetricsValueType.MULTI_INTS;
                threshold.setType(MetricsValueType.MULTI_INTS);
            } else if (metrics instanceof LabeledValueHolder) {
                if (((LabeledValueHolder) metrics).getValue().keys().stream()
                    .noneMatch(label -> validate(
                        label,
                        includeLabels,
                        excludeLabels,
                        includeLabelsRegex,
                        excludeLabelsRegex))) {
                    return;
                }
                valueType = MetricsValueType.LABELED_LONG;
                threshold.setType(MetricsValueType.LONG);
            } else {
                log.warn("Unsupported value type {}", valueType);
                return;
            }
        }

        if (valueType != null) {
            Window window = windows.computeIfAbsent(meta, ignored -> new Window(period));
            window.add(metrics);
        }
    }

    /**
     * Validate target whether matching rules which is included list, excludes list, include regular expression
     * or exclude regular expression.
     */
    private boolean validate(String target, List<String> includeList, List<String> excludeList,
        Pattern includeRegex, Pattern excludeRegex) {
        if (CollectionUtils.isNotEmpty(includeList)) {
            if (!includeList.contains(target)) {
                if (log.isTraceEnabled()) {
                    log.trace("{} isn't in the including list {}", target, includeList);
                }
                return false;
            }
        }

        if (CollectionUtils.isNotEmpty(excludeList)) {
            if (excludeList.contains(target)) {
                if (log.isTraceEnabled()) {
                    log.trace("{} is in the excluding list {}", target, excludeList);
                }
                return false;
            }
        }

        if (includeRegex != null) {
            if (!includeRegex.matcher(target).matches()) {
                if (log.isTraceEnabled()) {
                    log.trace("{} doesn't match the include regex {}", target, includeRegex);
                }
                return false;
            }
        }

        if (excludeRegex != null) {
            if (excludeRegex.matcher(target).matches()) {
                if (log.isTraceEnabled()) {
                    log.trace("{} matches the exclude regex {}", target, excludeRegex);
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Move the buffer window to give time.
     *
     * @param targetTime of moving target
     */
    public void moveTo(LocalDateTime targetTime) {
        windows.values().forEach(window -> window.moveTo(targetTime));
    }

    /**
     * Check the conditions, decide to whether trigger alarm.
     */
    public List<AlarmMessage> check() {
        List<AlarmMessage> alarmMessageList = new ArrayList<>(30);

        windows.forEach((meta, window) -> {
            Optional<AlarmMessage> alarmMessageOptional = window.checkAlarm();
            if (alarmMessageOptional.isPresent()) {
                AlarmMessage alarmMessage = alarmMessageOptional.get();
                alarmMessage.setScopeId(meta.getScopeId());
                alarmMessage.setScope(meta.getScope());
                alarmMessage.setName(meta.getName());
                alarmMessage.setId0(meta.getId0());
                alarmMessage.setId1(meta.getId1());
                alarmMessage.setRuleName(this.ruleName);
                alarmMessage.setAlarmMessage(formatter.format(meta));
                alarmMessage.setOnlyAsCondition(this.onlyAsCondition);
                alarmMessage.setStartTime(System.currentTimeMillis());
                alarmMessageList.add(alarmMessage);
            }
        });

        return alarmMessageList;
    }

    /**
     * A metrics window, based on AlarmRule#period. This window slides with time, just keeps the recent N(period)
     * buckets.
     */
    public class Window {
        private LocalDateTime endTime;
        private int period;
        private int counter;
        private int silenceCountdown;

        private LinkedList<Metrics> values;
        private ReentrantLock lock = new ReentrantLock();

        public Window(int period) {
            this.period = period;
            // -1 means silence countdown is not running.
            silenceCountdown = -1;
            counter = 0;
            init();
        }

        public void moveTo(LocalDateTime current) {
            lock.lock();
            try {
                if (endTime == null) {
                    init();
                } else {
                    int minutes = Minutes.minutesBetween(endTime, current).getMinutes();
                    if (minutes <= 0) {
                        return;
                    }
                    if (minutes > values.size()) {
                        // re-init
                        init();
                    } else {
                        for (int i = 0; i < minutes; i++) {
                            values.removeFirst();
                            values.addLast(null);
                        }
                    }
                }
                endTime = current;
            } finally {
                lock.unlock();
            }
            if (log.isTraceEnabled()) {
                log.trace("Move window {}", transformValues(values));
            }
        }

        public void add(Metrics metrics) {
            long bucket = metrics.getTimeBucket();

            LocalDateTime timeBucket = TIME_BUCKET_FORMATTER.parseLocalDateTime(bucket + "");

            this.lock.lock();
            try {
                if (this.endTime == null) {
                    init();
                    this.endTime = timeBucket;
                }
                int minutes = Minutes.minutesBetween(timeBucket, this.endTime).getMinutes();
                if (minutes < 0) {
                    this.moveTo(timeBucket);
                    minutes = 0;
                }

                if (minutes >= values.size()) {
                    // too old data
                    // also should happen, but maybe if agent/probe mechanism time is not right.
                    if (log.isTraceEnabled()) {
                        log.trace("Timebucket is {}, endTime is {} and value size is {}", timeBucket, this.endTime, values.size());
                    }
                    return;
                }

                this.values.set(values.size() - minutes - 1, metrics);
            } finally {
                this.lock.unlock();
            }
            if (log.isTraceEnabled()) {
                log.trace("Add metric {} to window {}", metrics, transformValues(this.values));
            }
        }

        public Optional<AlarmMessage> checkAlarm() {
            if (isMatch()) {
                /*
                 * When
                 * 1. Metrics value threshold triggers alarm by rule
                 * 2. Counter reaches the count threshold;
                 * 3. Isn't in silence stage, judged by SilenceCountdown(!=0).
                 */
                counter++;
                if (counter >= countThreshold && silenceCountdown < 1) {
                    silenceCountdown = silencePeriod;
                    return Optional.of(new AlarmMessage());
                } else {
                    silenceCountdown--;
                }
            } else {
                silenceCountdown--;
                if (counter > 0) {
                    counter--;
                }
            }
            return Optional.empty();
        }

        private boolean isMatch() {
            int matchCount = 0;
            for (Metrics metrics : values) {
                if (metrics == null) {
                    continue;
                }

                switch (valueType) {
                    case LONG:
                        long lvalue = ((LongValueHolder) metrics).getValue();
                        long lexpected = RunningRule.this.threshold.getLongThreshold();
                        if (op.test(lexpected, lvalue)) {
                            matchCount++;
                        }
                        break;
                    case INT:
                        int ivalue = ((IntValueHolder) metrics).getValue();
                        int iexpected = RunningRule.this.threshold.getIntThreshold();
                        if (op.test(iexpected, ivalue)) {
                            matchCount++;
                        }
                        break;
                    case DOUBLE:
                        double dvalue = ((DoubleValueHolder) metrics).getValue();
                        double dexpected = RunningRule.this.threshold.getDoubleThreshold();
                        if (op.test(dexpected, dvalue)) {
                            matchCount++;
                        }
                        break;
                    case MULTI_INTS:
                        int[] ivalueArray = ((MultiIntValuesHolder) metrics).getValues();
                        Integer[] iaexpected = RunningRule.this.threshold.getIntValuesThreshold();
                        if (log.isTraceEnabled()) {
                            log.trace("Value array is {}, expected array is {}", ivalueArray, iaexpected);
                        }
                        for (int i = 0; i < ivalueArray.length; i++) {
                            ivalue = ivalueArray[i];
                            Integer iNullableExpected = 0;
                            if (iaexpected.length > i) {
                                iNullableExpected = iaexpected[i];
                                if (iNullableExpected == null) {
                                    continue;
                                }
                            }
                            if (op.test(iNullableExpected, ivalue)) {
                                if (log.isTraceEnabled()) {
                                    log.trace("Matched, expected {}, value {}", iNullableExpected, ivalue);
                                }
                                matchCount++;
                                break;
                            }
                        }
                        break;
                    case LABELED_LONG:
                        DataTable values = ((LabeledValueHolder) metrics).getValue();
                        lexpected = RunningRule.this.threshold.getLongThreshold();
                        if (values.keys().stream().anyMatch(label ->
                            validate(
                                label,
                                RunningRule.this.includeLabels,
                                RunningRule.this.excludeLabels,
                                RunningRule.this.includeLabelsRegex,
                                RunningRule.this.excludeLabelsRegex)
                            && op.test(lexpected, values.get(label)))) {
                            matchCount++;
                        }
                        break;
                }
            }

            if (log.isTraceEnabled()) {
                log.trace("Match count is {}, threshold is {}", matchCount, countThreshold);
            }
            // Reach the threshold in current bucket.
            return matchCount >= countThreshold;
        }

        private void init() {
            values = new LinkedList<>();
            for (int i = 0; i < period; i++) {
                values.add(null);
            }
        }
    }

    private LinkedList<TraceLogMetric> transformValues(final LinkedList<Metrics> values) {
        LinkedList<TraceLogMetric> r = new LinkedList<>();
        values.forEach(m -> {
            if (m == null) {
                r.add(null);
                return;
            }
            switch (valueType) {
                case LONG:
                    r.add(new TraceLogMetric(m.getTimeBucket(), new Number[] {((LongValueHolder) m).getValue()}));
                    break;
                case INT:
                    r.add(new TraceLogMetric(m.getTimeBucket(), new Number[] {((IntValueHolder) m).getValue()}));
                    break;
                case DOUBLE:
                    r.add(new TraceLogMetric(m.getTimeBucket(), new Number[] {((DoubleValueHolder) m).getValue()}));
                    break;
                case MULTI_INTS:
                    int[] iArr = ((MultiIntValuesHolder) m).getValues();
                    r.add(new TraceLogMetric(m.getTimeBucket(), Arrays.stream(iArr).boxed().toArray(Number[]::new)));
                    break;
                case LABELED_LONG:
                    DataTable dt = ((LabeledValueHolder) m).getValue();
                    TraceLogMetric l = new TraceLogMetric(m.getTimeBucket(), dt.sortedValues(Comparator.naturalOrder()).toArray(new Number[0]));
                    l.labels = dt.sortedKeys(Comparator.naturalOrder()).toArray(new String[0]);
                    r.add(l);
            }
        });
        return r;
    }

    @RequiredArgsConstructor
    @ToString
    private static class TraceLogMetric {
        private final long timeBucket;
        private final Number[] value;
        private String[] labels;
    }
}
