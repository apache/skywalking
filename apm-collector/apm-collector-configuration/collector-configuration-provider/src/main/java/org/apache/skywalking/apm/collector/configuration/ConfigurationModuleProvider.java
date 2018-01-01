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

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends Module> module() {
        return ConfigurationModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        this.registerServiceImplementation(IApdexThresholdService.class, new ApdexThresholdService());
        this.registerServiceImplementation(IServiceAlarmRuleConfig.class, new ServiceAlarmRuleConfig());
        this.registerServiceImplementation(IInstanceAlarmRuleConfig.class, new InstanceAlarmRuleConfig());
        this.registerServiceImplementation(IApplicationAlarmRuleConfig.class, new ApplicationAlarmRuleConfig());
        this.registerServiceImplementation(IServiceReferenceAlarmRuleConfig.class, new ServiceReferenceAlarmRuleConfig());
        this.registerServiceImplementation(IInstanceReferenceAlarmRuleConfig.class, new InstanceReferenceAlarmRuleConfig());
        this.registerServiceImplementation(IApplicationReferenceAlarmRuleConfig.class, new ApplicationReferenceAlarmRuleConfig());
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {

    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}
