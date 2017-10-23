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

package org.skywalking.apm.collector.modulization;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * A module definition.
 *
 * @author wu-sheng
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

    void prepare(ModuleManager moduleManager,
        ApplicationConfiguration.ModuleConfiguration configuration) throws ProviderNotFoundException {
        ServiceLoader<ModuleProvider> moduleProviderLoader = ServiceLoader.load(ModuleProvider.class);
        boolean providerExist = false;
        for (ModuleProvider provider : moduleProviderLoader) {
            providerExist = true;
            if (provider.module().equals(getClass())) {
                ModuleProvider newProvider;
                try {
                    newProvider = provider.getClass().getConstructor(ModuleManager.class, Module.class).newInstance(moduleManager, this);
                } catch (InstantiationException e) {
                    throw new ProviderNotFoundException(e);
                } catch (IllegalAccessException e) {
                    throw new ProviderNotFoundException(e);
                } catch (InvocationTargetException e) {
                    throw new ProviderNotFoundException(e);
                } catch (NoSuchMethodException e) {
                    throw new ProviderNotFoundException(e);
                }
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
    public List<ModuleProvider> providers() throws ProviderNotFoundException {
        if (loadedProviders.size() == 0) {
            throw new ProviderNotFoundException("no provider exists.");
        }

        return loadedProviders;
    }

    public ModuleProvider provider() throws ProviderNotFoundException, DuplicateProviderException {
        if (loadedProviders.size() == 0) {
            throw new ProviderNotFoundException("no provider exists.");
        } else if (loadedProviders.size() > 1) {
            throw new DuplicateProviderException("exist " + loadedProviders.size() + " providers");
        }

        return loadedProviders.getFirst();
    }
}
