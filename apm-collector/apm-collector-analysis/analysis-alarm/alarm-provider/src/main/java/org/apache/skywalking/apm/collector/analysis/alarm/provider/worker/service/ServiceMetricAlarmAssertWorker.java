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
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IServiceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.alarm.AlarmType;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarm;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetric;

/**
 * @author peng-yongsheng
 */
public class ServiceMetricAlarmAssertWorker extends AlarmAssertWorker<ServiceMetric, ServiceAlarm> {

    private final ServiceNameCacheService serviceNameCacheService;
    private final IServiceAlarmRuleConfig serviceAlarmRuleConfig;

    public ServiceMetricAlarmAssertWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
        this.serviceAlarmRuleConfig = moduleManager.find(ConfigurationModule.NAME).getService(IServiceAlarmRuleConfig.class);
    }

    @Override public int id() {
        return AlarmWorkerIdDefine.SERVICE_METRIC_ALARM_ASSERT_WORKER_ID;
    }

    @Override protected ServiceAlarm newAlarmObject(String id, ServiceMetric inputMetric) {
        ServiceAlarm serviceAlarm = new ServiceAlarm();
        serviceAlarm.setId(id + Const.ID_SPLIT + inputMetric.getServiceId());
        serviceAlarm.setApplicationId(inputMetric.getApplicationId());
        serviceAlarm.setInstanceId(inputMetric.getInstanceId());
        serviceAlarm.setServiceId(inputMetric.getServiceId());
        return serviceAlarm;
    }

    @Override protected void generateAlarmContent(ServiceAlarm alarm, double threshold) {
        ServiceName serviceName = serviceNameCacheService.get(alarm.getServiceId());

        String clientOrServer = "server";
        if (MetricSource.Caller.getValue() == alarm.getSourceValue()) {
            clientOrServer = "client";
        }

        if (AlarmType.ERROR_RATE.getValue() == alarm.getAlarmType()) {
            alarm.setAlarmContent("The success rate of " + serviceName.getServiceName() + ", detected from " + clientOrServer + " side, is lower than " + threshold + " rate.");
        } else if (AlarmType.SLOW_RTT.getValue() == alarm.getAlarmType()) {
            alarm.setAlarmContent("Response time of " + serviceName.getServiceName() + ", detected from " + clientOrServer + " side, is slower than " + threshold + " ms.");
        }
    }

    @Override protected Double calleeErrorRateThreshold() {
        return serviceAlarmRuleConfig.calleeErrorRateThreshold();
    }

    @Override protected Double callerErrorRateThreshold() {
        return serviceAlarmRuleConfig.callerErrorRateThreshold();
    }

    @Override protected Double calleeAverageResponseTimeThreshold() {
        return serviceAlarmRuleConfig.calleeAverageResponseTimeThreshold();
    }

    @Override protected Double callerAverageResponseTimeThreshold() {
        return serviceAlarmRuleConfig.callerAverageResponseTimeThreshold();
    }

    public static class Factory extends AlarmAssertWorkerProvider<ServiceMetric, ServiceAlarm, ServiceMetricAlarmAssertWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ServiceMetricAlarmAssertWorker workerInstance(ModuleManager moduleManager) {
            return new ServiceMetricAlarmAssertWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
