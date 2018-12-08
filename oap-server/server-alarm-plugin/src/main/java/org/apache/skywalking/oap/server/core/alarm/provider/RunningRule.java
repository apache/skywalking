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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;
import org.apache.skywalking.oap.server.core.analysis.indicator.DoubleValueHolder;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.indicator.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.indicator.LongValueHolder;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.joda.time.LocalDateTime;
import org.joda.time.Minutes;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RunningRule represents each rule in running status. Based on the {@link AlarmRule} definition,
 *
 * @author wusheng
 */
public class RunningRule {
    private static final Logger logger = LoggerFactory.getLogger(RunningRule.class);
    private static DateTimeFormatter TIME_BUCKET_FORMATTER = DateTimeFormat.forPattern("yyyyMMddHHmm");

    private String ruleName;
    private int period;
    private String indicatorName;
    private final Threshold threshold;
    private final OP op;
    private final int countThreshold;
    private final int silencePeriod;
    private Map<MetaInAlarm, Window> windows;
    private volatile IndicatorValueType valueType;
    private Scope targetScope;
    private List<String> includeNames;
    private AlarmMessageFormatter formatter;

    public RunningRule(AlarmRule alarmRule) {
        indicatorName = alarmRule.getIndicatorName();
        this.ruleName = alarmRule.getAlarmRuleName();

        // Init the empty window for alarming rule.
        windows = new ConcurrentHashMap<>();

        period = alarmRule.getPeriod();

        threshold = new Threshold(alarmRule.getAlarmRuleName(), alarmRule.getThreshold());
        op = OP.get(alarmRule.getOp());

        this.countThreshold = alarmRule.getCount();
        this.silencePeriod = alarmRule.getSilencePeriod();

        this.includeNames = alarmRule.getIncludeNames();
        this.formatter = new AlarmMessageFormatter(alarmRule.getMessage());
    }

    /**
     * Receive indicator result from persistence, after it is saved into storage. In alarm, only minute dimensionality
     * indicators are expected to process.
     *
     * @param indicator
     */
    public void in(MetaInAlarm meta, Indicator indicator) {
        if (!meta.getIndicatorName().equals(indicatorName)) {
            //Don't match rule, exit.
            return;
        }

        if (CollectionUtils.isNotEmpty(includeNames)) {
            if (!includeNames.contains(meta.getName())) {
                return;
            }
        }

        if (valueType == null) {
            if (indicator instanceof LongValueHolder) {
                valueType = IndicatorValueType.LONG;
                threshold.setType(IndicatorValueType.LONG);
            } else if (indicator instanceof IntValueHolder) {
                valueType = IndicatorValueType.INT;
                threshold.setType(IndicatorValueType.INT);
            } else if (indicator instanceof DoubleValueHolder) {
                valueType = IndicatorValueType.DOUBLE;
                threshold.setType(IndicatorValueType.DOUBLE);
            } else {
                return;
            }
            targetScope = meta.getScope();
        }

        if (valueType != null) {
            Window window = windows.get(meta);
            if (window == null) {
                window = new Window(period);
                LocalDateTime timebucket = TIME_BUCKET_FORMATTER.parseLocalDateTime(indicator.getTimeBucket() + "");
                window.moveTo(timebucket);
                windows.put(meta, window);
            }

            window.add(indicator);
        }
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

        windows.entrySet().forEach(entry -> {
            MetaInAlarm meta = entry.getKey();
            Window window = entry.getValue();
            AlarmMessage alarmMessage = window.checkAlarm();
            if (alarmMessage != AlarmMessage.NONE) {
                alarmMessage.setScope(meta.getScope());
                alarmMessage.setName(meta.getName());
                alarmMessage.setId0(meta.getId0());
                alarmMessage.setId1(meta.getId1());
                alarmMessage.setAlarmMessage(formatter.format(meta));
                alarmMessage.setStartTime(System.currentTimeMillis());
                alarmMessageList.add(alarmMessage);
            }
        });

        return alarmMessageList;
    }

    /**
     * A indicator window, based on {@link AlarmRule#period}. This window slides with time, just keeps the recent
     * N(period) buckets.
     *
     * @author wusheng
     */
    public class Window {
        private LocalDateTime endTime;
        private int period;
        private int counter;
        private int silenceCountdown;

        private LinkedList<Indicator> values;
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
                    endTime = current;
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
                    endTime = current;
                }
            } finally {
                lock.unlock();
            }
        }

        public void add(Indicator indicator) {
            long bucket = indicator.getTimeBucket();

            LocalDateTime timebucket = TIME_BUCKET_FORMATTER.parseLocalDateTime(bucket + "");

            int minutes = Minutes.minutesBetween(timebucket, endTime).getMinutes();
            if (minutes == -1) {
                this.moveTo(timebucket);

            }

            lock.lock();
            try {
                if (minutes < 0) {
                    moveTo(timebucket);
                    minutes = 0;
                }

                if (minutes >= values.size()) {
                    // too old data
                    // also should happen, but maybe if agent/probe mechanism time is not right.
                    return;
                }

                values.set(values.size() - minutes - 1, indicator);
            } finally {
                lock.unlock();
            }
        }

        public AlarmMessage checkAlarm() {
            if (isMatch()) {
                /**
                 * When
                 * 1. Metric value threshold triggers alarm by rule
                 * 2. Counter reaches the count threshold;
                 * 3. Isn't in silence stage, judged by SilenceCountdown(!=0).
                 */
                counter++;
                if (counter >= countThreshold && silenceCountdown < 1) {
                    silenceCountdown = silencePeriod;

                    // set empty message, but new message
                    AlarmMessage message = new AlarmMessage();
                    return message;
                } else {
                    silenceCountdown--;
                }
            } else {
                silenceCountdown--;
                if (counter > 0) {
                    counter--;
                }
            }
            return AlarmMessage.NONE;
        }

        private boolean isMatch() {
            int matchCount = 0;
            for (Indicator indicator : values) {
                if (indicator == null) {
                    continue;
                }

                switch (valueType) {
                    case LONG:
                        long lvalue = ((LongValueHolder)indicator).getValue();
                        long lexpected = RunningRule.this.threshold.getLongThreshold();
                        switch (op) {
                            case GREATER:
                                if (lvalue > lexpected)
                                    matchCount++;
                                break;
                            case LESS:
                                if (lvalue < lexpected)
                                    matchCount++;
                                break;
                            case EQUAL:
                                if (lvalue == lexpected)
                                    matchCount++;
                                break;
                        }
                        break;
                    case INT:
                        int ivalue = ((IntValueHolder)indicator).getValue();
                        int iexpected = RunningRule.this.threshold.getIntThreshold();
                        switch (op) {
                            case LESS:
                                if (ivalue < iexpected)
                                    matchCount++;
                                break;
                            case GREATER:
                                if (ivalue > iexpected)
                                    matchCount++;
                                break;
                            case EQUAL:
                                if (ivalue == iexpected)
                                    matchCount++;
                                break;
                        }
                        break;
                    case DOUBLE:
                        double dvalue = ((DoubleValueHolder)indicator).getValue();
                        double dexpected = RunningRule.this.threshold.getDoubleThreadhold();
                        switch (op) {
                            case EQUAL:
                                // NOTICE: double equal is not reliable in Java,
                                // match result is not predictable
                                if (dvalue == dexpected)
                                    matchCount++;
                                break;
                            case GREATER:
                                if (dvalue > dexpected)
                                    matchCount++;
                                break;
                            case LESS:
                                if (dvalue < dexpected)
                                    matchCount++;
                                break;
                        }
                        break;
                }
            }

            // Reach the threshold in current bucket.
            return matchCount >= countThreshold;
        }

        private void init() {
            values = new LinkedList();
            for (int i = 0; i < period; i++) {
                values.add(null);
            }
        }
    }
}
