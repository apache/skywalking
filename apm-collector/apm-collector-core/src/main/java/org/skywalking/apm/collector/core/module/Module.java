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

package org.skywalking.apm.collector.core.module;

import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * A module definition.
 *
 * @author wu-sheng, peng-yongsheng
 */
public abstract class Module {
    private LinkedList<ModuleProvider> loadedProviders = new LinkedList<>();

    /**
     * @return the module name
     */
    public abstract String name();

    /**
     * @return the {@link Service} provided by this module.
     */
    public abstract Class<? extends Service>[] services();

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
            throw new ProviderNotFoundException("no provider exists.");
        }

        for (ModuleProvider moduleProvider : loadedProviders) {
            moduleProvider.prepare(configuration.getProviderConfiguration(moduleProvider.name()));
        }
    }

    void init(ModuleManager moduleManager,
        ApplicationConfiguration.ModuleConfiguration configuration) throws ProviderNotFoundException, ModuleNotFoundException, ServiceNotProvidedException {
        for (ModuleProvider provider : loadedProviders) {
            String[] requiredModules = provider.requiredModules();
            if (requiredModules != null) {
                for (String module : requiredModules) {
                    if (!moduleManager.has(module)) {
                        throw new ModuleNotFoundException(module + " is required by " + name() + ", but not found.");
                    }
                }
            }
            provider.init(configuration.getProviderConfiguration(provider.name()));

            provider.requiredCheck(services());
        }
    }

    /**
     * @return providers of this module
     */
    public final List<ModuleProvider> providers() throws ProviderNotFoundException {
        if (loadedProviders.size() == 0) {
            throw new ProviderNotFoundException("no provider exists.");
        }

        return loadedProviders;
    }

    public final ModuleProvider provider() throws ProviderNotFoundException, DuplicateProviderException {
        if (loadedProviders.size() == 0) {
            throw new ProviderNotFoundException("no provider exists.");
        } else if (loadedProviders.size() > 1) {
            throw new DuplicateProviderException("exist " + loadedProviders.size() + " providers");
        }

        return loadedProviders.getFirst();
    }
}
