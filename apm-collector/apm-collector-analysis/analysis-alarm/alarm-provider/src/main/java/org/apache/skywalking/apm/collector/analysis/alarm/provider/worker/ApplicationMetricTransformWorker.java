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

import org.apache.skywalking.apm.collector.analysis.alarm.define.graph.AlarmWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.AggregationWorker;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.table.alarm.AlarmMetric;
import org.apache.skywalking.apm.collector.storage.table.alarm.Layer;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetric;

/**
 * @author peng-yongsheng
 */
public class ApplicationMetricTransformWorker extends AggregationWorker<ApplicationMetric, AlarmMetric> {

    public ApplicationMetricTransformWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return AlarmWorkerIdDefine.APPLICATION_METRIC_TRANSFORM_WORKER_ID;
    }

    @Override protected AlarmMetric transform(ApplicationMetric applicationMetric) {
        AlarmMetric alarmMetric = new AlarmMetric(String.valueOf(applicationMetric.getApplicationId()));
        alarmMetric.setLayer(Layer.APPLICATION.getValue());
        alarmMetric.setObjectId(applicationMetric.getApplicationId());
        alarmMetric.setApplicationId(applicationMetric.getApplicationId());
        alarmMetric.setSourceValue(applicationMetric.getSourceValue());

        alarmMetric.setTransactionCalls(applicationMetric.getTransactionCalls());
        alarmMetric.setTransactionDurationSum(applicationMetric.getTransactionDurationSum());
        alarmMetric.setTransactionErrorCalls(applicationMetric.getTransactionErrorCalls());
        alarmMetric.setTransactionErrorDurationSum(applicationMetric.getTransactionErrorDurationSum());

        alarmMetric.setBusinessTransactionCalls(applicationMetric.getBusinessTransactionCalls());
        alarmMetric.setBusinessTransactionDurationSum(applicationMetric.getBusinessTransactionDurationSum());
        alarmMetric.setBusinessTransactionErrorCalls(applicationMetric.getBusinessTransactionErrorCalls());
        alarmMetric.setBusinessTransactionErrorDurationSum(applicationMetric.getBusinessTransactionErrorDurationSum());

        alarmMetric.setMqTransactionCalls(applicationMetric.getMqTransactionCalls());
        alarmMetric.setMqTransactionDurationSum(applicationMetric.getMqTransactionDurationSum());
        alarmMetric.setMqTransactionErrorCalls(applicationMetric.getMqTransactionErrorCalls());
        alarmMetric.setMqTransactionErrorDurationSum(applicationMetric.getMqTransactionErrorDurationSum());

        alarmMetric.setTimeBucket(applicationMetric.getTimeBucket());
        return alarmMetric;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ApplicationMetric, AlarmMetric, ApplicationMetricTransformWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ApplicationMetricTransformWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationMetricTransformWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
