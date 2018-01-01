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
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetric;

/**
 * @author peng-yongsheng
 */
public class InstanceMetricTransformWorker extends AggregationWorker<InstanceMetric, AlarmMetric> {

    public InstanceMetricTransformWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return AlarmWorkerIdDefine.INSTANCE_METRIC_TRANSFORM_WORKER_ID;
    }

    @Override protected AlarmMetric transform(InstanceMetric instanceMetric) {

        AlarmMetric alarmMetric = new AlarmMetric(String.valueOf(instanceMetric.getApplicationId()));
        alarmMetric.setLayer(Layer.INSTANCE.getValue());
        alarmMetric.setObjectId(instanceMetric.getApplicationId());
        alarmMetric.setApplicationId(instanceMetric.getApplicationId());
        alarmMetric.setSourceValue(instanceMetric.getSourceValue());

        alarmMetric.setTransactionCalls(instanceMetric.getTransactionCalls());
        alarmMetric.setTransactionDurationSum(instanceMetric.getTransactionDurationSum());
        alarmMetric.setTransactionErrorCalls(instanceMetric.getTransactionErrorCalls());
        alarmMetric.setTransactionErrorDurationSum(instanceMetric.getTransactionErrorDurationSum());

        alarmMetric.setBusinessTransactionCalls(instanceMetric.getBusinessTransactionCalls());
        alarmMetric.setBusinessTransactionDurationSum(instanceMetric.getBusinessTransactionDurationSum());
        alarmMetric.setBusinessTransactionErrorCalls(instanceMetric.getBusinessTransactionErrorCalls());
        alarmMetric.setBusinessTransactionErrorDurationSum(instanceMetric.getBusinessTransactionErrorDurationSum());

        alarmMetric.setMqTransactionCalls(instanceMetric.getMqTransactionCalls());
        alarmMetric.setMqTransactionDurationSum(instanceMetric.getMqTransactionDurationSum());
        alarmMetric.setMqTransactionErrorCalls(instanceMetric.getMqTransactionErrorCalls());
        alarmMetric.setMqTransactionErrorDurationSum(instanceMetric.getMqTransactionErrorDurationSum());

        alarmMetric.setTimeBucket(instanceMetric.getTimeBucket());
        return alarmMetric;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<InstanceMetric, AlarmMetric, InstanceMetricTransformWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public InstanceMetricTransformWorker workerInstance(ModuleManager moduleManager) {
            return new InstanceMetricTransformWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
