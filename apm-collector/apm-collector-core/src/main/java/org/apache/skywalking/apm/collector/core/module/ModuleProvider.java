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

package org.apache.skywalking.apm.collector.core.module;

import java.util.HashMap;
import java.util.Map;

/**
 * The <code>ModuleProvider</code> is an implementation of a {@link Module}.
 *
 * And each module can have one or more implementation, which depends on `application.yml`
 *
 * @author wu-sheng, peng-yongsheng
 */
public abstract class ModuleProvider {
    private ModuleManager manager;
    private Module module;
    private Map<Class<? extends Service>, Service> services = new HashMap<>();

    public ModuleProvider() {
    }

    void setManager(ModuleManager manager) {
        this.manager = manager;
    }

    void setModule(Module module) {
        this.module = module;
    }

    protected final ModuleManager getManager() {
        return manager;
    }

    /**
     * @return the name of this provider.
     */
    public abstract String name();

    /**
     * @return the module name
     */
    public abstract Class<? extends Module> module();

    /**
     * @return ModuleConfig
     */
    public abstract ModuleConfig createConfigBeanIfAbsent();

    /**
     * In prepare stage, the module should initialize things which are irrelative other modules.
     */
    public abstract void prepare() throws ServiceNotProvidedException;

    /**
     * In start stage, the module has been ready for interop.
     */
    public abstract void start() throws ServiceNotProvidedException, ModuleStartException;

    /**
     * This callback executes after all modules start up successfully.
     */
    public abstract void notifyAfterCompleted() throws ServiceNotProvidedException;

    /**
     * @return module names which does this module require?
     */
    public abstract String[] requiredModules();

    /**
     * Register a implementation for the service of this module provider.
     */
    protected final void registerServiceImplementation(Class<? extends Service> serviceType,
        Service service) throws ServiceNotProvidedException {
        if (serviceType.isInstance(service)) {
            this.services.put(serviceType, service);
        } else {
            throw new ServiceNotProvidedException(serviceType + " is not implemented by " + service);
        }
    }

    /**
     * Make sure all required services have been implemented.
     *
     * @param requiredServices must be implemented by the module.
     * @throws ServiceNotProvidedException when exist unimplemented service.
     */
    void requiredCheck(Class<? extends Service>[] requiredServices) throws ServiceNotProvidedException {
        if (requiredServices == null)
            return;

        for (Class<? extends Service> service : requiredServices) {
            if (!services.containsKey(service)) {
                throw new ServiceNotProvidedException("Service:" + service.getName() + " not provided");
            }
        }

        if (requiredServices.length != services.size()) {
            throw new ServiceNotProvidedException("The " + this.name() + " provider in " + module.name() + " module provide more service implementations than Module requirements.");
        }
    }

    @SuppressWarnings("unchecked") <T extends Service> T getService(
        Class<T> serviceType) throws ServiceNotProvidedException {
        Service serviceImpl = services.get(serviceType);
        if (serviceImpl != null) {
            return (T)serviceImpl;
        }

        throw new ServiceNotProvidedException("Service " + serviceType.getName() + " should not be provided, based on module define.");
    }

    Module getModule() {
        return module;
    }

    String getModuleName() {
        return module.name();
    }
}
