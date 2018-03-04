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

package org.apache.skywalking.apm.collector.analysis.alarm.provider.worker;

import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorker;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerException;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.NumberFormatUtils;
import org.apache.skywalking.apm.collector.storage.table.Metric;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.alarm.Alarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.AlarmType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public abstract class AlarmAssertWorker<INPUT extends StreamData & Metric, OUTPUT extends StreamData & Alarm> extends AbstractLocalAsyncWorker<INPUT, OUTPUT> {

    private final Logger logger = LoggerFactory.getLogger(AlarmAssertWorker.class);

    public AlarmAssertWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override protected final void onWork(INPUT inputMetric) throws WorkerException {
        errorRateAlarmAssert(inputMetric);
        averageResponseTimeAlarmAssert(inputMetric);
    }

    protected abstract OUTPUT newAlarmObject(String id, INPUT inputMetric);

    protected abstract void generateAlarmContent(OUTPUT alarm, double threshold);

    protected abstract Double calleeErrorRateThreshold();

    protected abstract Double callerErrorRateThreshold();

    private void errorRateAlarmAssert(INPUT inputMetric) {
        Double errorRate = Double.valueOf(inputMetric.getTransactionErrorCalls()) / Double.valueOf(inputMetric.getTransactionCalls());
        errorRate = NumberFormatUtils.rateNumberFormat(errorRate);

        if (inputMetric.getSourceValue().equals(MetricSource.Callee.getValue())) {
            if (errorRate >= calleeErrorRateThreshold()) {
                String id = String.valueOf(MetricSource.Callee.getValue()) + Const.ID_SPLIT + AlarmType.ERROR_RATE.getValue();
                OUTPUT alarm = newAlarmObject(id, inputMetric);
                alarm.setAlarmType(AlarmType.ERROR_RATE.getValue());
                alarm.setLastTimeBucket(inputMetric.getTimeBucket());
                alarm.setSourceValue(MetricSource.Callee.getValue());
                generateAlarmContent(alarm, calleeErrorRateThreshold());

                onNext(alarm);
            }
        } else if (inputMetric.getSourceValue().equals(MetricSource.Caller.getValue())) {
            if (errorRate >= callerErrorRateThreshold()) {
                String id = String.valueOf(MetricSource.Caller.getValue()) + Const.ID_SPLIT + AlarmType.ERROR_RATE.getValue();
                OUTPUT alarm = newAlarmObject(id, inputMetric);
                alarm.setAlarmType(AlarmType.ERROR_RATE.getValue());
                alarm.setLastTimeBucket(inputMetric.getTimeBucket());
                alarm.setSourceValue(MetricSource.Caller.getValue());
                generateAlarmContent(alarm, callerErrorRateThreshold());

                onNext(alarm);
            }
        } else {
            logger.error("Please check the metric source, the value must be {} or {}, but {}", MetricSource.Caller.getValue(), MetricSource.Callee.getValue(), inputMetric.getSourceValue());
        }
    }

    protected abstract Double calleeAverageResponseTimeThreshold();

    protected abstract Double callerAverageResponseTimeThreshold();

    private void averageResponseTimeAlarmAssert(INPUT inputMetric) {
        Long transactionSuccessDurationSum = inputMetric.getTransactionDurationSum() - inputMetric.getTransactionErrorDurationSum();
        Long transactionSuccessCalls = inputMetric.getTransactionCalls() - inputMetric.getTransactionErrorCalls();
        Double averageResponseTime = Double.valueOf(transactionSuccessDurationSum) / Double.valueOf(transactionSuccessCalls);

        if (inputMetric.getSourceValue().equals(MetricSource.Callee.getValue())) {
            if (averageResponseTime >= calleeAverageResponseTimeThreshold()) {
                String id = String.valueOf(MetricSource.Callee.getValue()) + Const.ID_SPLIT + AlarmType.SLOW_RTT.getValue();
                OUTPUT alarm = newAlarmObject(id, inputMetric);
                alarm.setAlarmType(AlarmType.SLOW_RTT.getValue());
                alarm.setLastTimeBucket(inputMetric.getTimeBucket());
                alarm.setSourceValue(MetricSource.Callee.getValue());
                generateAlarmContent(alarm, calleeAverageResponseTimeThreshold());

                onNext(alarm);
            }
        } else if (inputMetric.getSourceValue().equals(MetricSource.Caller.getValue())) {
            if (averageResponseTime >= callerAverageResponseTimeThreshold()) {
                String id = String.valueOf(MetricSource.Caller.getValue()) + Const.ID_SPLIT + AlarmType.SLOW_RTT.getValue();
                OUTPUT alarm = newAlarmObject(id, inputMetric);
                alarm.setAlarmType(AlarmType.SLOW_RTT.getValue());
                alarm.setLastTimeBucket(inputMetric.getTimeBucket());
                alarm.setSourceValue(MetricSource.Caller.getValue());
                generateAlarmContent(alarm, callerAverageResponseTimeThreshold());

                onNext(alarm);
            }
        } else {
            logger.error("Please check the metric source, the value must be {} or {}, but {}", MetricSource.Caller.getValue(), MetricSource.Callee.getValue(), inputMetric.getSourceValue());
        }
    }
}