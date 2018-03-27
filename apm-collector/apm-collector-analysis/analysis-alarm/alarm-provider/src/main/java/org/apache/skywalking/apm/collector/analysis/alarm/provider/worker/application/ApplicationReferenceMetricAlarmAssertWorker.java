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
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IApplicationReferenceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.alarm.AlarmType;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.register.Application;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceMetricAlarmAssertWorker extends AlarmAssertWorker<ApplicationReferenceMetric, ApplicationReferenceAlarm> {

    private final ApplicationCacheService applicationCacheService;
    private final IApplicationReferenceAlarmRuleConfig applicationReferenceAlarmRuleConfig;

    public ApplicationReferenceMetricAlarmAssertWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        this.applicationReferenceAlarmRuleConfig = moduleManager.find(ConfigurationModule.NAME).getService(IApplicationReferenceAlarmRuleConfig.class);
    }

    @Override public int id() {
        return AlarmWorkerIdDefine.APPLICATION_REFERENCE_METRIC_ALARM_ASSERT_WORKER_ID;
    }

    @Override protected ApplicationReferenceAlarm newAlarmObject(String id, ApplicationReferenceMetric inputMetric) {
        ApplicationReferenceAlarm applicationReferenceAlarm = new ApplicationReferenceAlarm();
        applicationReferenceAlarm.setId(id + Const.ID_SPLIT + inputMetric.getFrontApplicationId() + Const.ID_SPLIT + inputMetric.getBehindApplicationId());
        applicationReferenceAlarm.setFrontApplicationId(inputMetric.getFrontApplicationId());
        applicationReferenceAlarm.setBehindApplicationId(inputMetric.getBehindApplicationId());
        return applicationReferenceAlarm;
    }

    @Override protected void generateAlarmContent(ApplicationReferenceAlarm alarm, double threshold) {
        Application application = applicationCacheService.getApplicationById(alarm.getBehindApplicationId());

        String clientOrServer = "server";
        if (MetricSource.Caller.getValue() == alarm.getSourceValue()) {
            clientOrServer = "client";
        }

        if (AlarmType.ERROR_RATE.getValue() == alarm.getAlarmType()) {
            alarm.setAlarmContent("The success rate of " + application.getApplicationCode() + ", detected from " + clientOrServer + " side, is lower than " + threshold + " rate.");
        } else if (AlarmType.SLOW_RTT.getValue() == alarm.getAlarmType()) {
            alarm.setAlarmContent("Response time of " + application.getApplicationCode() + ", detected from " + clientOrServer + " side, is slower than " + threshold + " ms.");
        }
    }

    @Override protected Double calleeErrorRateThreshold() {
        return applicationReferenceAlarmRuleConfig.calleeErrorRateThreshold();
    }

    @Override protected Double callerErrorRateThreshold() {
        return applicationReferenceAlarmRuleConfig.callerErrorRateThreshold();
    }

    @Override protected Double calleeAverageResponseTimeThreshold() {
        return applicationReferenceAlarmRuleConfig.calleeAverageResponseTimeThreshold();
    }

    @Override protected Double callerAverageResponseTimeThreshold() {
        return applicationReferenceAlarmRuleConfig.callerAverageResponseTimeThreshold();
    }

    public static class Factory extends AlarmAssertWorkerProvider<ApplicationReferenceMetric, ApplicationReferenceAlarm, ApplicationReferenceMetricAlarmAssertWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ApplicationReferenceMetricAlarmAssertWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationReferenceMetricAlarmAssertWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
