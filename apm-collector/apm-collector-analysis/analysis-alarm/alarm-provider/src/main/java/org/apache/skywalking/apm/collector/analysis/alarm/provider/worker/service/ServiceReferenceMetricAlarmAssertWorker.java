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

package org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.service;

import org.apache.skywalking.apm.collector.analysis.alarm.define.graph.AlarmWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.AlarmAssertWorker;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.AlarmAssertWorkerProvider;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IServiceReferenceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceMetricAlarmAssertWorker extends AlarmAssertWorker<ServiceReferenceMetric, ServiceReferenceAlarm> {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceMetricAlarmAssertWorker.class);

    private final IServiceReferenceAlarmRuleConfig serviceReferenceAlarmRuleConfig;

    public ServiceReferenceMetricAlarmAssertWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.serviceReferenceAlarmRuleConfig = moduleManager.find(ConfigurationModule.NAME).getService(IServiceReferenceAlarmRuleConfig.class);
    }

    @Override public int id() {
        return AlarmWorkerIdDefine.SERVICE_REFERENCE_METRIC_ALARM_ASSERT_WORKER_ID;
    }

    @Override protected ServiceReferenceAlarm newAlarmObject(String id, ServiceReferenceMetric inputMetric) {
        ServiceReferenceAlarm serviceReferenceAlarm = new ServiceReferenceAlarm();
        serviceReferenceAlarm.setId(id + Const.ID_SPLIT + inputMetric.getFrontServiceId() + Const.ID_SPLIT + inputMetric.getBehindServiceId());
        serviceReferenceAlarm.setFrontApplicationId(inputMetric.getFrontApplicationId());
        serviceReferenceAlarm.setBehindApplicationId(inputMetric.getBehindApplicationId());
        serviceReferenceAlarm.setFrontInstanceId(inputMetric.getFrontInstanceId());
        serviceReferenceAlarm.setBehindInstanceId(inputMetric.getBehindInstanceId());
        serviceReferenceAlarm.setFrontServiceId(inputMetric.getFrontServiceId());
        serviceReferenceAlarm.setBehindServiceId(inputMetric.getBehindServiceId());
        return serviceReferenceAlarm;
    }

    @Override protected Double calleeErrorRateThreshold() {
        return serviceReferenceAlarmRuleConfig.calleeErrorRateThreshold();
    }

    @Override protected Double callerErrorRateThreshold() {
        return serviceReferenceAlarmRuleConfig.callerErrorRateThreshold();
    }

    @Override protected Double calleeAverageResponseTimeThreshold() {
        return serviceReferenceAlarmRuleConfig.calleeAverageResponseTimeThreshold();
    }

    @Override protected Double callerAverageResponseTimeThreshold() {
        return serviceReferenceAlarmRuleConfig.callerAverageResponseTimeThreshold();
    }

    public static class Factory extends AlarmAssertWorkerProvider<ServiceReferenceMetric, ServiceReferenceAlarm, ServiceReferenceMetricAlarmAssertWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ServiceReferenceMetricAlarmAssertWorker workerInstance(ModuleManager moduleManager) {
            return new ServiceReferenceMetricAlarmAssertWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
