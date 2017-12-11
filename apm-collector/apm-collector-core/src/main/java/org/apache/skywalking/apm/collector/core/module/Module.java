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

import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A module definition.
 *
 * @author wu-sheng, peng-yongsheng
 */
public abstract class Module {

    private final Logger logger = LoggerFactory.getLogger(Module.class);

    private LinkedList<ModuleProvider> loadedProviders = new LinkedList<>();

    /**
     * @return the module name
     */
    public abstract String name();

    /**
     * @return the {@link Service} provided by this module.
     */
    public abstract Class[] services();

    /**
     * Run the prepare stage for the module, including finding all potential providers, and asking them to prepare.
     *
     * @param moduleManager of this module
     * @param configuration of this module
     * @throws ProviderNotFoundException when even don't find a single one providers.
     */
    void prepare(ModuleManager moduleManager,
        ApplicationConfiguration.ModuleConfiguration configuration) throws ProviderNotFoundException, ServiceNotProvidedException {
        ServiceLoader<ModuleProvider> moduleProviderLoader = ServiceLoader.load(ModuleProvider.class);
        boolean providerExist = false;
        for (ModuleProvider provider : moduleProviderLoader) {
            if (!configuration.has(provider.name())) {
                continue;
            }

            providerExist = true;
            if (provider.module().equals(getClass())) {
                ModuleProvider newProvider;
                try {
                    newProvider = provider.getClass().newInstance();
                } catch (InstantiationException e) {
                    throw new ProviderNotFoundException(e);
                } catch (IllegalAccessException e) {
                    throw new ProviderNotFoundException(e);
                }
                newProvider.setManager(moduleManager);
                newProvider.setModule(this);
                loadedProviders.add(newProvider);
            }
        }

        if (!providerExist) {
            throw new ProviderNotFoundException(this.name() + " module no provider exists.");
        }

        for (ModuleProvider moduleProvider : loadedProviders) {
            logger.info("Prepare the {} provider in {} module.", moduleProvider.name(), this.name());
            moduleProvider.prepare(configuration.getProviderConfiguration(moduleProvider.name()));
        }
    }

    /**
     * @return providers of this module
     */
    final List<ModuleProvider> providers() {
        return loadedProviders;
    }

    final ModuleProvider provider() throws ProviderNotFoundException, DuplicateProviderException {
        if (loadedProviders.size() > 1) {
            throw new DuplicateProviderException(this.name() + " module exist " + loadedProviders.size() + " providers");
        }

        return loadedProviders.getFirst();
    }

    public final <T extends Service> T getService(Class<T> serviceType) throws ServiceNotProvidedRuntimeException {
        try {
            return provider().getService(serviceType);
        } catch (ProviderNotFoundException | DuplicateProviderException | ServiceNotProvidedException e) {
            throw new ServiceNotProvidedRuntimeException(e.getMessage());
        }
    }
}
