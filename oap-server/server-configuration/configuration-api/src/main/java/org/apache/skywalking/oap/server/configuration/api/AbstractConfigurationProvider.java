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

package org.apache.skywalking.oap.server.configuration.api;

import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

/**
 * The recommendation default base implementor of Configuration module. The real implementor could extend this provider
 * to make a new one, easily.
 */
public abstract class AbstractConfigurationProvider extends ModuleProvider {
    private ConfigWatcherRegister configWatcherRegister;

    @Override
    public Class<? extends ModuleDefine> module() {
        return ConfigurationModule.class;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        configWatcherRegister = initConfigReader();
        this.registerServiceImplementation(DynamicConfigurationService.class, configWatcherRegister);
    }

    protected abstract ConfigWatcherRegister initConfigReader() throws ModuleStartException;

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        configWatcherRegister.start();
    }

    @Override
    public String[] requiredModules() {
        return new String[0];
    }

}
