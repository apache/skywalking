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

package org.apache.skywalking.apm.collector.cache.caffeine;

import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.caffeine.service.ApplicationCacheCaffeineService;
import org.apache.skywalking.apm.collector.cache.caffeine.service.InstanceCacheCaffeineService;
import org.apache.skywalking.apm.collector.cache.caffeine.service.NetworkAddressCacheCaffeineService;
import org.apache.skywalking.apm.collector.cache.caffeine.service.ServiceIdCacheCaffeineService;
import org.apache.skywalking.apm.collector.cache.caffeine.service.ServiceNameCacheCaffeineService;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.apache.skywalking.apm.collector.cache.service.NetworkAddressCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceIdCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.core.module.ModuleDefine;
import org.apache.skywalking.apm.collector.storage.StorageModule;

/**
 * @author peng-yongsheng
 */
public class CacheModuleCaffeineProvider extends ModuleProvider {

    private final CacheModuleCaffeineConfig config;

    public CacheModuleCaffeineProvider() {
        super();
        this.config = new CacheModuleCaffeineConfig();
    }

    @Override public String name() {
        return "caffeine";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return CacheModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        this.registerServiceImplementation(ApplicationCacheService.class, new ApplicationCacheCaffeineService(getManager()));
        this.registerServiceImplementation(InstanceCacheService.class, new InstanceCacheCaffeineService(getManager()));
        this.registerServiceImplementation(ServiceIdCacheService.class, new ServiceIdCacheCaffeineService(getManager()));
        this.registerServiceImplementation(ServiceNameCacheService.class, new ServiceNameCacheCaffeineService(getManager()));
        this.registerServiceImplementation(NetworkAddressCacheService.class, new NetworkAddressCacheCaffeineService(getManager()));
    }

    @Override public void start() {
    }

    @Override public void notifyAfterCompleted() {
    }

    @Override public String[] requiredModules() {
        return new String[] {StorageModule.NAME};
    }
}
