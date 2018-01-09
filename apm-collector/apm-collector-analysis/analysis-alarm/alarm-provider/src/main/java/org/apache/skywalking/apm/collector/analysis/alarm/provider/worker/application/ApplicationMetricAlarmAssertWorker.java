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

package org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.application;

import org.apache.skywalking.apm.collector.analysis.alarm.define.graph.AlarmWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.AlarmAssertWorker;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.AlarmAssertWorkerProvider;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IApplicationAlarmRuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarm;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetric;

/**
 * @author peng-yongsheng
 */
public class ApplicationMetricAlarmAssertWorker extends AlarmAssertWorker<ApplicationMetric, ApplicationAlarm> {

    private final IApplicationAlarmRuleConfig applicationAlarmRuleConfig;

    public ApplicationMetricAlarmAssertWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.applicationAlarmRuleConfig = moduleManager.find(ConfigurationModule.NAME).getService(IApplicationAlarmRuleConfig.class);
    }

    @Override public int id() {
        return AlarmWorkerIdDefine.APPLICATION_METRIC_ALARM_ASSERT_WORKER_ID;
    }

    @Override protected ApplicationAlarm newAlarmObject(String id, ApplicationMetric inputMetric) {
        ApplicationAlarm applicationAlarm = new ApplicationAlarm();
        applicationAlarm.setId(id + Const.ID_SPLIT + inputMetric.getApplicationId());
        applicationAlarm.setApplicationId(inputMetric.getApplicationId());
        return applicationAlarm;
    }

    @Override protected Double calleeErrorRateThreshold() {
        return applicationAlarmRuleConfig.calleeErrorRateThreshold();
    }

    @Override protected Double callerErrorRateThreshold() {
        return applicationAlarmRuleConfig.callerErrorRateThreshold();
    }

    @Override protected Double calleeAverageResponseTimeThreshold() {
        return applicationAlarmRuleConfig.calleeAverageResponseTimeThreshold();
    }

    @Override protected Double callerAverageResponseTimeThreshold() {
        return applicationAlarmRuleConfig.callerAverageResponseTimeThreshold();
    }

    public static class Factory extends AlarmAssertWorkerProvider<ApplicationMetric, ApplicationAlarm, ApplicationMetricAlarmAssertWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ApplicationMetricAlarmAssertWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationMetricAlarmAssertWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
