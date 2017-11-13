/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.cache.guava;

import java.util.Properties;
import org.skywalking.apm.collector.cache.CacheModule;
import org.skywalking.apm.collector.cache.guava.service.ApplicationCacheGuavaService;
import org.skywalking.apm.collector.cache.guava.service.InstanceCacheGuavaService;
import org.skywalking.apm.collector.cache.guava.service.ServiceIdCacheGuavaService;
import org.skywalking.apm.collector.cache.guava.service.ServiceNameCacheGuavaService;
import org.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.skywalking.apm.collector.cache.service.ServiceIdCacheService;
import org.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.core.module.ModuleProvider;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.service.DAOService;

/**
 * @author peng-yongsheng
 */
public class CacheModuleGuavaProvider extends ModuleProvider {

    @Override public String name() {
        return "guava";
    }

    @Override public Class<? extends Module> module() {
        return CacheModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        DAOService daoService = getManager().find(StorageModule.NAME).getService(DAOService.class);

        this.registerServiceImplementation(ApplicationCacheService.class, new ApplicationCacheGuavaService(daoService));
        this.registerServiceImplementation(InstanceCacheService.class, new InstanceCacheGuavaService(daoService));
        this.registerServiceImplementation(ServiceIdCacheService.class, new ServiceIdCacheGuavaService(daoService));
        this.registerServiceImplementation(ServiceNameCacheService.class, new ServiceNameCacheGuavaService(daoService));
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[] {StorageModule.NAME};
    }
}
