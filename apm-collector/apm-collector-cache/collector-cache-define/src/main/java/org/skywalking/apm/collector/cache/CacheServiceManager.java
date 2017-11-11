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

    public void setApplicationCacheService(
        ApplicationCacheService applicationCacheService) {
        this.applicationCacheService = applicationCacheService;
    }

    public InstanceCacheService getInstanceCacheService() {
        return instanceCacheService;
    }

    public void setInstanceCacheService(InstanceCacheService instanceCacheService) {
        this.instanceCacheService = instanceCacheService;
    }

    public ServiceIdCacheService getServiceIdCacheService() {
        return serviceIdCacheService;
    }

    public void setServiceIdCacheService(ServiceIdCacheService serviceIdCacheService) {
        this.serviceIdCacheService = serviceIdCacheService;
    }

    public ServiceNameCacheService getServiceNameCacheService() {
        return serviceNameCacheService;
    }

    public void setServiceNameCacheService(
        ServiceNameCacheService serviceNameCacheService) {
        this.serviceNameCacheService = serviceNameCacheService;
    }
}
