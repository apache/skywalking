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

package org.skywalking.apm.collector.cache;

import org.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.skywalking.apm.collector.cache.service.ServiceIdCacheService;
import org.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.core.module.ModuleNotFoundException;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;

/**
 * @author peng-yongsheng
 */
public class CacheServiceManager {

    private ApplicationCacheService applicationCacheService;
    private InstanceCacheService instanceCacheService;
    private ServiceIdCacheService serviceIdCacheService;
    private ServiceNameCacheService serviceNameCacheService;

    public ApplicationCacheService getApplicationCacheService() {
        return applicationCacheService;
    }

    public InstanceCacheService getInstanceCacheService() {
        return instanceCacheService;
    }

    public ServiceIdCacheService getServiceIdCacheService() {
        return serviceIdCacheService;
    }

    public ServiceNameCacheService getServiceNameCacheService() {
        return serviceNameCacheService;
    }

    public void init(ModuleManager moduleManager) throws ModuleNotFoundException, ServiceNotProvidedException {
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        this.instanceCacheService = moduleManager.find(CacheModule.NAME).getService(InstanceCacheService.class);
        this.serviceIdCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceIdCacheService.class);
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
    }
}
