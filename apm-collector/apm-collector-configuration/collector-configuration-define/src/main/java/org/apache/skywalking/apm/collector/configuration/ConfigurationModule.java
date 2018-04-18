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

import org.apache.skywalking.apm.collector.configuration.service.IApdexThresholdService;
import org.apache.skywalking.apm.collector.configuration.service.IApplicationAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.IApplicationReferenceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.ICollectorConfig;
import org.apache.skywalking.apm.collector.configuration.service.IComponentLibraryCatalogService;
import org.apache.skywalking.apm.collector.configuration.service.IInstanceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.IInstanceReferenceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.IServiceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.configuration.service.IServiceReferenceAlarmRuleConfig;
import org.apache.skywalking.apm.collector.core.module.Module;

/**
 * @author peng-yongsheng
 */
public class ConfigurationModule extends Module {

    public static final String NAME = "configuration";

    @Override public String name() {
        return NAME;
    }

    @Override public Class[] services() {
        return new Class[] {
            ICollectorConfig.class,
            IApdexThresholdService.class,
            IServiceAlarmRuleConfig.class, IInstanceAlarmRuleConfig.class, IApplicationAlarmRuleConfig.class,
            IServiceReferenceAlarmRuleConfig.class, IInstanceReferenceAlarmRuleConfig.class, IApplicationReferenceAlarmRuleConfig.class,
            IComponentLibraryCatalogService.class};
    }
}
