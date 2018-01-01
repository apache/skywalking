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

package org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.instance;

import org.apache.skywalking.apm.collector.analysis.alarm.define.graph.AlarmWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.AlarmAssertWorker;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.AlarmAssertWorkerProvider;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IInstanceReferenceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class InstanceReferenceMetricAlarmAssertWorker extends AlarmAssertWorker<InstanceReferenceMetric, InstanceReferenceAlarm> {

    private final IInstanceReferenceAlarmRuleConfig instanceReferenceAlarmRuleConfig;

    public InstanceReferenceMetricAlarmAssertWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.instanceReferenceAlarmRuleConfig = moduleManager.find(ConfigurationModule.NAME).getService(IInstanceReferenceAlarmRuleConfig.class);
    }

    @Override public int id() {
        return AlarmWorkerIdDefine.INSTANCE_REFERENCE_METRIC_ALARM_ASSERT_WORKER_ID;
    }

    @Override protected InstanceReferenceAlarm newAlarmObject(String id, InstanceReferenceMetric inputMetric) {
        InstanceReferenceAlarm instanceReferenceAlarm = new InstanceReferenceAlarm(id + Const.ID_SPLIT + inputMetric.getFrontInstanceId() + Const.ID_SPLIT + inputMetric.getBehindInstanceId());
        instanceReferenceAlarm.setFrontApplicationId(inputMetric.getFrontApplicationId());
        instanceReferenceAlarm.setBehindApplicationId(inputMetric.getBehindApplicationId());
        instanceReferenceAlarm.setFrontInstanceId(inputMetric.getFrontInstanceId());
        instanceReferenceAlarm.setBehindInstanceId(inputMetric.getBehindInstanceId());
        return instanceReferenceAlarm;
    }

    @Override protected Double calleeErrorRateThreshold() {
        return instanceReferenceAlarmRuleConfig.calleeErrorRateThreshold();
    }

    @Override protected Double callerErrorRateThreshold() {
        return instanceReferenceAlarmRuleConfig.callerErrorRateThreshold();
    }

    @Override protected Double calleeAverageResponseTimeThreshold() {
        return instanceReferenceAlarmRuleConfig.calleeAverageResponseTimeThreshold();
    }

    @Override protected Double callerAverageResponseTimeThreshold() {
        return instanceReferenceAlarmRuleConfig.callerAverageResponseTimeThreshold();
    }

    public static class Factory extends AlarmAssertWorkerProvider<InstanceReferenceMetric, InstanceReferenceAlarm, InstanceReferenceMetricAlarmAssertWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public InstanceReferenceMetricAlarmAssertWorker workerInstance(ModuleManager moduleManager) {
            return new InstanceReferenceMetricAlarmAssertWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
