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

package org.apache.skywalking.oap.server.starter;

import java.util.*;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.library.module.*;

/**
 * @author peng-yongsheng
 */
public class ModuleManagerImpl implements ModuleManager {

    private final ApplicationConfiguration applicationConfiguration;
    private final Map<String, ModuleDefine> modules;

    public ModuleManagerImpl(ApplicationConfiguration applicationConfiguration) {
        this.applicationConfiguration = applicationConfiguration;
        this.modules = new HashMap<>();
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleConfigException, ProviderNotFoundException, ModuleStartException {
        CoreModule coreModule = new CoreModule();
        ClusterModule clusterModule = new ClusterModule();
        StorageModule storageModule = new StorageModule();

        init(coreModule);
        init(clusterModule);
        init(storageModule);

        coreModule.provider().start();
        storageModule.provider().start();
        clusterModule.provider().start();

        coreModule.provider().notifyAfterCompleted();
        storageModule.provider().notifyAfterCompleted();
        clusterModule.provider().notifyAfterCompleted();
    }

    @Override public void init(
        ModuleDefine moduleDefine) throws ServiceNotProvidedException, ModuleConfigException, ProviderNotFoundException {
        if (!applicationConfiguration.has(moduleDefine.name())) {
            throw new ModuleConfigException("Can't found core module configuration, please check the application.yml file.");
        }

        moduleDefine.prepare(this, applicationConfiguration.getModuleConfiguration(moduleDefine.name()));
        modules.put(moduleDefine.name(), moduleDefine);
    }

    @Override public ModuleDefine find(String moduleName) throws ModuleNotFoundRuntimeException {
        ModuleDefine module = modules.get(moduleName);
        if (module != null)
            return module;
        throw new ModuleNotFoundRuntimeException(moduleName + " missing.");
    }
}
