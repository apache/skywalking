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

package org.apache.skywalking.apm.collector.analysis.register.provider;

import java.util.Properties;
import org.apache.skywalking.apm.collector.analysis.register.define.AnalysisRegisterModule;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IApplicationIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IInstanceIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IServiceNameService;
import org.apache.skywalking.apm.collector.analysis.register.provider.service.ApplicationIDService;
import org.apache.skywalking.apm.collector.analysis.register.provider.service.InstanceIDService;
import org.apache.skywalking.apm.collector.analysis.register.provider.service.ServiceNameService;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;

/**
 * @author peng-yongsheng
 */
public class AnalysisRegisterModuleProvider extends ModuleProvider {

    public static final String NAME = "default";

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends Module> module() {
        return AnalysisRegisterModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        this.registerServiceImplementation(IApplicationIDService.class, new ApplicationIDService(getManager()));
        this.registerServiceImplementation(IInstanceIDService.class, new InstanceIDService(getManager()));
        this.registerServiceImplementation(IServiceNameService.class, new ServiceNameService(getManager()));
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {

    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}
