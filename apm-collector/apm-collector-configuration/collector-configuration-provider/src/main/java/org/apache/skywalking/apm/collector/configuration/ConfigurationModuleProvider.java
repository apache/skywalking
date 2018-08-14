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

import org.apache.skywalking.apm.collector.configuration.service.*;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.core.util.*;

/**
 * @author peng-yongsheng
 */
public class ConfigurationModuleProvider extends ModuleProvider {

    private final ConfigurationModuleConfig config;

    public ConfigurationModuleProvider() {
        super();
        this.config = new ConfigurationModuleConfig();
    }

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return ConfigurationModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        String namespace = StringUtils.isNotEmpty(config.getNamespace()) ? config.getNamespace() : Const.EMPTY_STRING;
        int applicationApdexThreshold = config.getApplicationApdexThreshold() == 0 ? 2000 : config.getApplicationApdexThreshold();
        double serviceErrorRateThreshold = config.getServiceErrorRateThreshold() == 0 ? 0.10 : config.getServiceErrorRateThreshold() / 100;
        int serviceAverageResponseTimeThreshold = config.getServiceAverageResponseTimeThreshold() == 0 ? 2000 : config.getServiceAverageResponseTimeThreshold();
        double instanceErrorRateThreshold = config.getInstanceErrorRateThreshold() == 0 ? 0.10 : config.getInstanceErrorRateThreshold() / 100;
        int instanceAverageResponseTimeThreshold = config.getInstanceAverageResponseTimeThreshold() == 0 ? 2000 : config.getInstanceAverageResponseTimeThreshold();
        double applicationErrorRateThreshold = config.getApplicationErrorRateThreshold() == 0 ? 0.10 : config.getApplicationErrorRateThreshold() / 100;
        int applicationAverageResponseTimeThreshold = config.getApplicationAverageResponseTimeThreshold() == 0 ? 2000 : config.getApplicationAverageResponseTimeThreshold();

        int thermodynamicResponseTimeStep = config.getThermodynamicResponseTimeStep() == 0 ? 50 : config.getThermodynamicResponseTimeStep();
        int thermodynamicCountOfResponseTimeSteps = config.getThermodynamicCountOfResponseTimeSteps() == 0 ? 40 : config.getThermodynamicCountOfResponseTimeSteps();

        int workerCacheMaxSize = config.getWorkerCacheMaxSize() == 0 ? 10000 : config.getWorkerCacheMaxSize();

        this.registerServiceImplementation(ICollectorConfig.class, new CollectorConfigService(namespace));
        this.registerServiceImplementation(IComponentLibraryCatalogService.class, new ComponentLibraryCatalogService());
        this.registerServiceImplementation(IApdexThresholdService.class, new ApdexThresholdService(applicationApdexThreshold));
        this.registerServiceImplementation(IServiceAlarmRuleConfig.class, new ServiceAlarmRuleConfig(serviceErrorRateThreshold, serviceAverageResponseTimeThreshold));
        this.registerServiceImplementation(IInstanceAlarmRuleConfig.class, new InstanceAlarmRuleConfig(instanceErrorRateThreshold, instanceAverageResponseTimeThreshold));
        this.registerServiceImplementation(IApplicationAlarmRuleConfig.class, new ApplicationAlarmRuleConfig(applicationErrorRateThreshold, applicationAverageResponseTimeThreshold));
        this.registerServiceImplementation(IServiceReferenceAlarmRuleConfig.class, new ServiceReferenceAlarmRuleConfig(serviceErrorRateThreshold, serviceAverageResponseTimeThreshold));
        this.registerServiceImplementation(IInstanceReferenceAlarmRuleConfig.class, new InstanceReferenceAlarmRuleConfig(instanceErrorRateThreshold, instanceAverageResponseTimeThreshold));
        this.registerServiceImplementation(IApplicationReferenceAlarmRuleConfig.class, new ApplicationReferenceAlarmRuleConfig(applicationErrorRateThreshold, applicationAverageResponseTimeThreshold));
        this.registerServiceImplementation(IResponseTimeDistributionConfigService.class, new ResponseTimeDistributionConfigService(thermodynamicResponseTimeStep, thermodynamicCountOfResponseTimeSteps));
        this.registerServiceImplementation(IWorkerCacheSizeConfig.class, new WorkerCacheSizeConfigService(workerCacheMaxSize));
    }

    @Override public void start() {
    }

    @Override public void notifyAfterCompleted() {
    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}
