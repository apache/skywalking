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

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.skywalking.mqe.rt.exception.ParseErrorListener;
import org.apache.skywalking.mqe.rt.grammar.MQELexer;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecoveryMessage;
import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.provider.expr.rt.AlarmMQEVisitor;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.DoubleValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResultType;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValues;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.joda.time.LocalDateTime;
import org.joda.time.Minutes;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

/**
 * RunningRule represents each rule in running status. Based on the {@link AlarmRule} definition,
 */
@Slf4j
@Getter
public class RunningRule {
    private static DateTimeFormatter TIME_BUCKET_FORMATTER = DateTimeFormat.forPattern("yyyyMMddHHmm");

    private final String ruleName;
    private final int period;
    private final String expression;
    private final int silencePeriod;
    private final int recoveryObservationPeriod;
    private final Map<AlarmEntity, Window> windows;
    private final List<String> includeNames;
    private final List<String> excludeNames;
    private final Pattern includeNamesRegex;
    private final Pattern excludeNamesRegex;
    private final AlarmMessageFormatter formatter;
    private final List<Tag> tags;
    private final Set<String> hooks;
    private final Set<String> includeMetrics;
    private final ParseTree exprTree;
    // The additional period is used to calculate the trend.
    private final int additionalPeriod;
    private final ModuleManager moduleManager;

    public RunningRule(AlarmRule alarmRule, ModuleManager moduleManager) {
        expression = alarmRule.getExpression();
        this.ruleName = alarmRule.getAlarmRuleName();
        this.includeMetrics = alarmRule.getIncludeMetrics();
        // Init the empty window for alarming rule.
        windows = new ConcurrentHashMap<>();
        period = alarmRule.getPeriod();
        this.silencePeriod = alarmRule.getSilencePeriod();
        this.recoveryObservationPeriod = alarmRule.getRecoveryObservationPeriod();
        this.includeNames = alarmRule.getIncludeNames();
        this.excludeNames = alarmRule.getExcludeNames();
        this.includeNamesRegex = StringUtil.isNotEmpty(alarmRule.getIncludeNamesRegex()) ?
                Pattern.compile(alarmRule.getIncludeNamesRegex()) : null;
        this.excludeNamesRegex = StringUtil.isNotEmpty(alarmRule.getExcludeNamesRegex()) ?
                Pattern.compile(alarmRule.getExcludeNamesRegex()) : null;
        this.formatter = new AlarmMessageFormatter(alarmRule.getMessage());
        this.tags = alarmRule.getTags()
                .entrySet()
                .stream()
                .map(e -> new Tag(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        this.hooks = alarmRule.getHooks();
        MQELexer lexer = new MQELexer(CharStreams.fromString(alarmRule.getExpression()));
        MQEParser parser = new MQEParser(new CommonTokenStream(lexer));
        parser.addErrorListener(new ParseErrorListener());
        this.exprTree = parser.expression();
        this.additionalPeriod = alarmRule.getMaxTrendRange();
        this.moduleManager = moduleManager;
    }

    /**
     * Receive metrics result from persistence, after it is saved into storage. In alarm, only minute dimensionality
     * metrics are expected to process.
     *
     * @param meta    of input metrics
     * @param metrics includes the values.
     */
    public void in(MetaInAlarm meta, Metrics metrics) {
        if (!includeMetrics.contains(meta.getMetricsName())) {
            //Don't match rule, exit.
            if (log.isTraceEnabled()) {
                log.trace("Metric name not in the expression, {}-{}", expression, meta.getMetricsName());
            }
            return;
        }

        final String metaName = meta.getName();
        if (!validate(metaName, includeNames, excludeNames, includeNamesRegex, excludeNamesRegex)) {
            return;
        }

        AlarmEntity entity = new AlarmEntity(
                meta.getScope(), meta.getScopeId(), meta.getName(), meta.getId0(), meta.getId1());

        Window window = windows.computeIfAbsent(entity, ignored -> new Window(entity, this.period,
                this.silencePeriod, this.recoveryObservationPeriod, this.additionalPeriod));
        window.add(meta.getMetricsName(), metrics);
    }

    /**
     * Validate target whether matching rules which is included list, excludes list, include regular expression or
     * exclude regular expression.
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
        // Truncate targetTime to minute, make sure the second is `00` and milliseconds is `00` such as: 18:30:00.000
        final LocalDateTime target = targetTime.withSecondOfMinute(0).withMillisOfSecond(0);
        windows.values().forEach(window -> window.moveTo(target));
    }

    /**
     * Check the conditions, decide to whether trigger alarm.
     */
    public List<AlarmMessage> check() {
        List<AlarmMessage> alarmMessageList = new ArrayList<>(30);
        List<AlarmEntity> expiredEntityList = new ArrayList<>();

        windows.forEach((alarmEntity, window) -> {
            if (window.isExpired()) {
                expiredEntityList.add(alarmEntity);
                if (log.isTraceEnabled()) {
                    log.trace("RuleName:{} AlarmEntity {} {} {} expired", ruleName, alarmEntity.getName(),
                            alarmEntity.getId0(), alarmEntity.getId1());
                }
                return;
            }

            Optional<AlarmMessage> alarmMessageOptional = window.checkAlarm();
            alarmMessageOptional.ifPresent(alarmMessageList::add);
        });

        expiredEntityList.forEach(windows::remove);
        return alarmMessageList;
    }

    public enum State {
        NORMAL,
        FIRING,
        SILENCED_FIRING,
        OBSERVING_RECOVERY,
        RECOVERED
    }

    /**
     * A metrics window, based on AlarmRule#period. This window slides with time, just keeps the recent N(period)
     * buckets.
     */
    public class Window {

        @Getter
        private LocalDateTime endTime;
        @Getter
        private final int additionalPeriod;
        @Getter
        private final int size;
        @Getter
        private final int period;
        @Getter
        private final AlarmStateMachine stateMachine;
        private LinkedList<Map<String, Metrics>> values;
        private ReentrantLock lock = new ReentrantLock();
        @Getter
        private AlarmMessage lastAlarmMessage;
        @Getter
        private JsonObject mqeMetricsSnapshot;
        private AlarmEntity entity;

        public Window(AlarmEntity entity, int period, int silencePeriod, int recoveryObservationPeriod,
                      int additionalPeriod) {
            this.entity = entity;
            this.additionalPeriod = additionalPeriod;
            this.size = period + additionalPeriod;
            this.period = period;
            this.stateMachine = new AlarmStateMachine(silencePeriod, recoveryObservationPeriod);
            this.init();
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

        public void add(String metricsName, Metrics metrics) {
            long bucket = metrics.getTimeBucket();

            LocalDateTime timeBucket = TIME_BUCKET_FORMATTER.parseLocalDateTime(bucket + "");

            this.lock.lock();
            try {
                if (this.endTime == null) {
                    init();
                    this.endTime = timeBucket;
                }
                int minutes = Minutes.minutesBetween(timeBucket, this.endTime).getMinutes();
                //timeBucket > endTime
                if (minutes < 0) {
                    this.moveTo(timeBucket);
                    minutes = 0;
                }

                if (minutes >= values.size()) {
                    // too old data
                    // also should happen, but maybe if agent/probe mechanism time is not right.
                    if (log.isTraceEnabled()) {
                        log.trace(
                                "Timebucket is {}, endTime is {} and value size is {}", timeBucket, this.endTime,
                                values.size()
                        );
                    }
                    return;
                }
                int index = values.size() - minutes - 1;
                Map<String, Metrics> metricsMap = values.get(index);
                if (metricsMap == null) {
                    metricsMap = new HashMap<>();
                    metricsMap.put(metricsName, metrics);
                    values.set(index, metricsMap);
                } else {
                    metricsMap.put(metricsName, metrics);
                }
            } finally {
                this.lock.unlock();
            }
            if (log.isTraceEnabled()) {
                log.trace("Add metric {} to window {}", metrics, transformValues(this.values));
            }
        }

        public Optional<AlarmMessage> checkAlarm() {
            boolean match = isMatch();
            if (log.isTraceEnabled()) {
                log.trace("RuleName {} AlarmEntity {} {} {} isMatch:{}", ruleName, entity.getName(), entity.getId0(),
                        entity.getId1(), match);
            }
            if (match) {
                stateMachine.onMatch();
            } else {
                stateMachine.onMismatch();
            }
            if (stateMachine.getCurrentState() == State.FIRING) {
                AlarmMessage alarmMessage = buildAlarmMessage();
                lastAlarmMessage = alarmMessage;
                return Optional.of(alarmMessage);
            }
            if (stateMachine.getCurrentState() == State.RECOVERED) {
                AlarmRecoveryMessage alarmRecoveryMessage = new AlarmRecoveryMessage(lastAlarmMessage);
                lastAlarmMessage = null;
                return Optional.of(alarmRecoveryMessage);
            }
            return Optional.empty();
        }

        private AlarmMessage buildAlarmMessage() {
            AlarmMessage alarmMessage = new AlarmMessage();
            alarmMessage.setScopeId(entity.getScopeId());
            alarmMessage.setScope(entity.getScope());
            alarmMessage.setName(entity.getName());
            alarmMessage.setId0(entity.getId0());
            alarmMessage.setId1(entity.getId1());
            alarmMessage.setRuleName(ruleName);
            alarmMessage.setAlarmMessage(formatter.format(entity));
            alarmMessage.setStartTime(System.currentTimeMillis());
            alarmMessage.setPeriod(period);
            alarmMessage.setTags(tags);
            alarmMessage.setHooks(hooks);
            alarmMessage.setExpression(expression);
            alarmMessage.setMqeMetricsSnapshot(mqeMetricsSnapshot);
            return alarmMessage;
        }

        private boolean isMatch() {
            this.lock.lock();
            int isMatch = 0;
            try {
                TRACE_CONTEXT.set(new DebuggingTraceContext(expression, false, false));
                AlarmMQEVisitor visitor = new AlarmMQEVisitor(moduleManager, this.entity, this.values, this.endTime, this.additionalPeriod);
                ExpressionResult parseResult = visitor.visit(exprTree);
                if (StringUtil.isNotBlank(parseResult.getError())) {
                    log.error("expression:" + expression + " error: " + parseResult.getError());
                    return false;
                }
                if (!parseResult.isBoolResult() ||
                        ExpressionResultType.SINGLE_VALUE != parseResult.getType() ||
                        CollectionUtils.isEmpty(parseResult.getResults())) {
                    return false;
                }
                if (!parseResult.isLabeledResult()) {
                    MQEValues mqeValues = parseResult.getResults().get(0);
                    if (mqeValues != null &&
                            CollectionUtils.isNotEmpty(mqeValues.getValues()) &&
                            mqeValues.getValues().get(0) != null) {
                        isMatch = (int) mqeValues.getValues().get(0).getDoubleValue();
                    }
                } else {
                    // if the result has multiple labels, when there is one label match, then the result is match
                    // for example in 5 minutes, the sum(percentile{p='50,75'} > 1000) >= 3
                    // percentile{p='50,75'} result is:
                    // P50(1000,1100,1200,1000,500), > 1000 2 times
                    // P75(2000,1500,1200,1000,500), > 1000 3 times
                    // percentile{p='50,75'} > 1000 result is:
                    // P50(0,1,1,0,0)
                    // P75(1,1,1,0,0)
                    // sum(percentile{p='50,75'} > 1000) >= 3 result is:
                    // P50(0)
                    // P75(1)
                    // then the isMatch is 1
                    for (MQEValues mqeValues : parseResult.getResults()) {
                        if (mqeValues != null &&
                                CollectionUtils.isNotEmpty(mqeValues.getValues()) &&
                                mqeValues.getValues().get(0) != null) {
                            isMatch = (int) mqeValues.getValues().get(0).getDoubleValue();
                            if (isMatch == 1) {
                                break;
                            }
                        }
                    }
                }
                if (log.isTraceEnabled()) {
                    log.trace("Match expression is {}", expression);
                }
                this.mqeMetricsSnapshot = visitor.getMqeMetricsSnapshot();
                return isMatch == 1;
            } finally {
                this.lock.unlock();
                TRACE_CONTEXT.remove();
            }
        }

        public boolean isExpired() {
            if (this.values != null) {
                for (Map<String, Metrics> value : this.values) {
                    if (value != null) {
                        return false;
                    }
                }
            }
            return true;
        }

        public void scanWindowValues(Consumer<LinkedList<Map<String, Metrics>>> scanFunction) {
            lock.lock();
            try {
                scanFunction.accept(values);
            } finally {
                lock.unlock();
            }
        }

        private void init() {
            values = new LinkedList<>();
            for (int i = 0; i < size; i++) {
                values.add(null);
            }
        }

        @Getter
        public class AlarmStateMachine {
            private int silenceCountdown;
            private int recoveryObservationCountdown;
            private final int silencePeriod;
            private final int recoveryObservationPeriod;
            private State currentState;

            public AlarmStateMachine(int silencePeriod, int recoveryObservationPeriod) {
                this.currentState = State.NORMAL;
                this.silencePeriod = silencePeriod;
                this.recoveryObservationPeriod = recoveryObservationPeriod;
                this.silenceCountdown = -1;
                this.recoveryObservationCountdown = recoveryObservationPeriod;
            }

            public void onMatch() {
                if (log.isTraceEnabled()) {
                    log.trace("RuleName:{} AlarmEntity {} {} {} onMatch silenceCountdown:{} currentState:{}",
                            ruleName, entity.getName(), entity.getId0(), entity.getId1(), silenceCountdown, currentState);
                }
                silenceCountdown--;
                switch (currentState) {
                    case NORMAL:
                        transitionTo(State.FIRING);
                        break;
                    case SILENCED_FIRING:
                    case OBSERVING_RECOVERY:
                    case RECOVERED:
                        if (silenceCountdown < 0) {
                            transitionTo(State.FIRING);
                        } else {
                            transitionTo(State.SILENCED_FIRING);
                        }
                        break;
                    case FIRING:
                        if (silenceCountdown >= 0) {
                            transitionTo(State.SILENCED_FIRING);
                        }
                        break;
                    default:
                        break;
                }
            }

            public void onMismatch() {
                if (log.isTraceEnabled()) {
                    log.trace("RuleName:{} AlarmEntity {} {} {} onMismatch silenceCountdown:{} " +
                                    "recoveryObservationCountdown:{} currentState:{}",
                            ruleName, entity.getName(), entity.getId0(), entity.getId1(), silenceCountdown,
                            recoveryObservationCountdown, currentState);
                }
                recoveryObservationCountdown--;
                silenceCountdown--;
                switch (currentState) {
                    case FIRING:
                    case SILENCED_FIRING:
                        if (this.recoveryObservationCountdown < 0 && silenceCountdown < 0) {
                            transitionTo(State.RECOVERED);
                        } else {
                            transitionTo(State.OBSERVING_RECOVERY);
                        }
                        break;
                    case OBSERVING_RECOVERY:
                        if (recoveryObservationCountdown < 0 && silenceCountdown < 0) {
                            transitionTo(State.RECOVERED);
                        }
                        break;
                    case RECOVERED:
                        transitionTo(State.NORMAL);
                        break;
                    case NORMAL:
                    default:
                        break;
                }
            }

            private void transitionTo(State newState) {
                if (log.isTraceEnabled()) {
                    log.trace("RuleName:{} AlarmEntity {} {} {} transitionTo  newState:{}",
                            ruleName, entity.getName(), entity.getId0(), entity.getId1(), newState);
                }
                this.currentState = newState;
                switch (newState) {
                    case NORMAL:
                        resetCountdowns();
                        break;
                    case FIRING:
                        this.silenceCountdown = this.silencePeriod;
                        this.recoveryObservationCountdown = this.recoveryObservationPeriod;
                        break;
                    case SILENCED_FIRING:
                        break;
                    case OBSERVING_RECOVERY:
                        this.recoveryObservationCountdown = this.recoveryObservationPeriod - 1;
                        break;
                    case RECOVERED:
                        this.recoveryObservationCountdown = this.recoveryObservationPeriod;
                        break;
                }
            }

            private void resetCountdowns() {
                this.recoveryObservationCountdown = this.recoveryObservationPeriod;
            }

        }

    }

    private LinkedList<Map<String, TraceLogMetric>> transformValues(LinkedList<Map<String, Metrics>> values) {
        LinkedList<Map<String, TraceLogMetric>> result = new LinkedList<>();
        for (Map<String, Metrics> value : values) {
            if (value == null) {
                result.add(null);
                continue;
            }
            value.forEach((name, m) -> {
                Map<String, TraceLogMetric> r = new HashMap<>();
                result.add(r);
                if (m instanceof LongValueHolder) {
                    r.put(name, new TraceLogMetric(m.getTimeBucket(), new Number[]{((LongValueHolder) m).getValue()}));
                } else if (m instanceof IntValueHolder) {
                    r.put(name, new TraceLogMetric(m.getTimeBucket(), new Number[]{((IntValueHolder) m).getValue()}));
                } else if (m instanceof DoubleValueHolder) {
                    r.put(name, new TraceLogMetric(m.getTimeBucket(), new Number[]{((DoubleValueHolder) m).getValue()}));
                } else if (m instanceof LabeledValueHolder) {
                    DataTable dt = ((LabeledValueHolder) m).getValue();
                    TraceLogMetric l = new TraceLogMetric(
                            m.getTimeBucket(), dt.sortedValues(Comparator.naturalOrder())
                            .toArray(new Number[0]));
                    l.labels = dt.sortedKeys(Comparator.naturalOrder()).toArray(new String[0]);
                    r.put(name, l);
                } else {
                    log.warn("Unsupported metrics {}", m);
                }
            });
        }
        return result;
    }

    @RequiredArgsConstructor
    @ToString
    private static class TraceLogMetric {
        private final long timeBucket;
        private final Number[] value;
        private String[] labels;
    }
}
