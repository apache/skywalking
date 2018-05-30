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

package org.apache.skywalking.apm.collector.cache.guava;

import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.guava.service.ApplicationCacheGuavaService;
import org.apache.skywalking.apm.collector.cache.guava.service.InstanceCacheGuavaService;
import org.apache.skywalking.apm.collector.cache.guava.service.NetworkAddressCacheGuavaService;
import org.apache.skywalking.apm.collector.cache.guava.service.ServiceIdCacheGuavaService;
import org.apache.skywalking.apm.collector.cache.guava.service.ServiceNameCacheGuavaService;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.apache.skywalking.apm.collector.cache.service.NetworkAddressCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceIdCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleDefine;
import org.apache.skywalking.apm.collector.core.module.ModuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.storage.StorageModule;

/**
 * @author peng-yongsheng
 */
public class CacheModuleGuavaProvider extends ModuleProvider {

    private final CacheModuleGuavaConfig config;

    public CacheModuleGuavaProvider() {
        super();
        this.config = new CacheModuleGuavaConfig();
    }

    @Override public String name() {
        return "guava";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return CacheModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        this.registerServiceImplementation(ApplicationCacheService.class, new ApplicationCacheGuavaService(getManager()));
        this.registerServiceImplementation(InstanceCacheService.class, new InstanceCacheGuavaService(getManager()));
        this.registerServiceImplementation(ServiceIdCacheService.class, new ServiceIdCacheGuavaService(getManager()));
        this.registerServiceImplementation(ServiceNameCacheService.class, new ServiceNameCacheGuavaService(getManager()));
        this.registerServiceImplementation(NetworkAddressCacheService.class, new NetworkAddressCacheGuavaService(getManager()));
    }

    @Override public void start() {
    }

    @Override public void notifyAfterCompleted() {
    }

    @Override public String[] requiredModules() {
        return new String[] {StorageModule.NAME};
    }
}
