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

package org.apache.skywalking.apm.collector.configuration;

import java.util.Properties;
import org.apache.skywalking.apm.collector.configuration.service.ApdexThresholdService;
import org.apache.skywalking.apm.collector.configuration.service.ApplicationAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.ApplicationReferenceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.IApdexThresholdService;
import org.apache.skywalking.apm.collector.configuration.service.IApplicationAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.IApplicationReferenceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.IInstanceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.IInstanceReferenceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.IServiceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.IServiceReferenceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.InstanceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.InstanceReferenceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.ServiceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.ServiceReferenceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;

/**
 * @author peng-yongsheng
 */
public class ConfigurationModuleProvider extends ModuleProvider {

    private static final String APPLICATION_APDEX_THRESHOLD = "application_apdex_threshold";
    private static final String SERVICE_ERROR_RATE_THRESHOLD = "service_error_rate_threshold";
    private static final String SERVICE_AVERAGE_RESPONSE_TIME_THRESHOLD = "service_average_response_time_threshold";
    private static final String INSTANCE_ERROR_RATE_THRESHOLD = "instance_error_rate_threshold";
    private static final String INSTANCE_AVERAGE_RESPONSE_TIME_THRESHOLD = "instance_average_response_time_threshold";
    private static final String APPLICATION_ERROR_RATE_THRESHOLD = "application_error_rate_threshold";
    private static final String APPLICATION_AVERAGE_RESPONSE_TIME_THRESHOLD = "application_average_response_time_threshold";

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends Module> module() {
        return ConfigurationModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        Integer applicationApdexThreshold = (Integer)config.getOrDefault(APPLICATION_APDEX_THRESHOLD, 2000);
        Double serviceErrorRateThreshold = (Double)config.getOrDefault(SERVICE_ERROR_RATE_THRESHOLD, 10.00);
        Integer serviceAverageResponseTimeThreshold = (Integer)config.getOrDefault(SERVICE_AVERAGE_RESPONSE_TIME_THRESHOLD, 2000);
        Double instanceErrorRateThreshold = (Double)config.getOrDefault(INSTANCE_ERROR_RATE_THRESHOLD, 10.00);
        Integer instanceAverageResponseTimeThreshold = (Integer)config.getOrDefault(INSTANCE_AVERAGE_RESPONSE_TIME_THRESHOLD, 2000);
        Double applicationErrorRateThreshold = (Double)config.getOrDefault(APPLICATION_ERROR_RATE_THRESHOLD, 10.00);
        Integer applicationAverageResponseTimeThreshold = (Integer)config.getOrDefault(APPLICATION_AVERAGE_RESPONSE_TIME_THRESHOLD, 2000);

        this.registerServiceImplementation(IApdexThresholdService.class, new ApdexThresholdService(applicationApdexThreshold));
        this.registerServiceImplementation(IServiceAlarmRuleConfig.class, new ServiceAlarmRuleConfig(serviceErrorRateThreshold, serviceAverageResponseTimeThreshold));
        this.registerServiceImplementation(IInstanceAlarmRuleConfig.class, new InstanceAlarmRuleConfig(instanceErrorRateThreshold, instanceAverageResponseTimeThreshold));
        this.registerServiceImplementation(IApplicationAlarmRuleConfig.class, new ApplicationAlarmRuleConfig(applicationErrorRateThreshold, applicationAverageResponseTimeThreshold));
        this.registerServiceImplementation(IServiceReferenceAlarmRuleConfig.class, new ServiceReferenceAlarmRuleConfig(serviceErrorRateThreshold, serviceAverageResponseTimeThreshold));
        this.registerServiceImplementation(IInstanceReferenceAlarmRuleConfig.class, new InstanceReferenceAlarmRuleConfig(instanceErrorRateThreshold, instanceAverageResponseTimeThreshold));
        this.registerServiceImplementation(IApplicationReferenceAlarmRuleConfig.class, new ApplicationReferenceAlarmRuleConfig(applicationErrorRateThreshold, applicationAverageResponseTimeThreshold));
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {

    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}
