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

package org.apache.skywalking.oap.server.library.module;

import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Setter;

/**
 * The <code>ModuleProvider</code> is an implementation of a {@link ModuleDefine}.
 * <p>
 * And each moduleDefine can have one or more implementation, which depends on `application.yml`
 */
public abstract class ModuleProvider implements ModuleServiceHolder {
    @Setter
    private ModuleManager manager;
    @Setter(AccessLevel.PACKAGE)
    private ModuleDefine moduleDefine;
    @Setter(AccessLevel.PACKAGE)
    private TerminalFriendlyTable bootingParameters;
    private final Map<Class<? extends Service>, Service> services = new HashMap<>();

    public ModuleProvider() {
    }

    protected final ModuleManager getManager() {
        return manager;
    }

    /**
     * @return the name of this provider.
     */
    public abstract String name();

    /**
     * @return the moduleDefine name
     */
    public abstract Class<? extends ModuleDefine> module();

    /**
     * Create a config creator to initialize this configuration of this module provider
     * @return creator instance to initialize the configuration with callback. Or return null if no config is required.
     */
    public abstract ConfigCreator<? extends ModuleConfig> newConfigCreator();

    /**
     * Configuration creator to provide Module Config to initialize
     * @param <T> class type of the config
     */
    public interface ConfigCreator<T extends ModuleConfig> {
        /**
         * Declare the type of the config class
         * @return class type
         */
        Class<T> type();

        /**
         * Callback when the ModuleManager kernel has initialized this configuration.
         * @param initialized instance of the given {@link #type()}
         */
        void onInitialized(T initialized);
    }

    /**
     * In prepare stage, the moduleDefine should initialize things which are irrelative other modules.
     */
    public abstract void prepare() throws ServiceNotProvidedException, ModuleStartException;

    /**
     * In start stage, the moduleDefine has been ready for interop.
     */
    public abstract void start() throws ServiceNotProvidedException, ModuleStartException;

    /**
     * This callback executes after all modules start up successfully.
     */
    public abstract void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException;

    /**
     * @return moduleDefine names which does this moduleDefine require?
     */
    public abstract String[] requiredModules();

    /**
     * Register an implementation for the service of this moduleDefine provider.
     */
    @Override
    public final void registerServiceImplementation(Class<? extends Service> serviceType,
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
     * @param requiredServices must be implemented by the moduleDefine.
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
            throw new ServiceNotProvidedException("The " + this.name() + " provider in " + moduleDefine.name() + " moduleDefine provide more service implementations than ModuleDefine requirements.");
        }
    }

    @Override
    public @SuppressWarnings("unchecked")
    <T extends Service> T getService(Class<T> serviceType) throws ServiceNotProvidedException {
        Service serviceImpl = services.get(serviceType);
        if (serviceImpl != null) {
            return (T) serviceImpl;
        }

        throw new ServiceNotProvidedException("Service " + serviceType.getName() + " should not be provided, based on moduleDefine define.");
    }

    public ModuleDefine getModule() {
        return moduleDefine;
    }

    String getModuleName() {
        return moduleDefine.name();
    }

    protected void setBootingParameter(String name, String value) {
        bootingParameters.addRow(new TerminalFriendlyTable.Row(name, value));
    }

    protected void setBootingParameter(String name, long value) {
        bootingParameters.addRow(new TerminalFriendlyTable.Row(name, String.valueOf(value)));
    }
}
