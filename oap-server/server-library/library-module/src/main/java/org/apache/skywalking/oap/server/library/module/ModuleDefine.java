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

import java.lang.reflect.Field;
import java.util.*;
import org.slf4j.*;

/**
 * A module definition.
 *
 * @author wu-sheng, peng-yongsheng
 */
public abstract class ModuleDefine implements ModuleProviderHolder {

    private static final Logger logger = LoggerFactory.getLogger(ModuleDefine.class);

    private final LinkedList<ModuleProvider> loadedProviders = new LinkedList<>();

    private final String name;

    public ModuleDefine(String name) {
        this.name = name;
    }

    /**
     * @return the module name
     */
    public final String name() {
        return name;
    }

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
        ApplicationConfiguration.ModuleConfiguration configuration) throws ProviderNotFoundException, ServiceNotProvidedException, ModuleConfigException, ModuleStartException {
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
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new ProviderNotFoundException(e);
                }
                newProvider.setManager(moduleManager);
                newProvider.setModuleDefine(this);
                loadedProviders.add(newProvider);
            }
        }

        if (!providerExist) {
            throw new ProviderNotFoundException(this.name() + " module no provider exists.");
        }

        for (ModuleProvider moduleProvider : loadedProviders) {
            logger.info("Prepare the {} provider in {} module.", moduleProvider.name(), this.name());
            try {
                copyProperties(moduleProvider.createConfigBeanIfAbsent(), configuration.getProviderConfiguration(moduleProvider.name()), this.name(), moduleProvider.name());
            } catch (IllegalAccessException e) {
                throw new ModuleConfigException(this.name() + " module config transport to config bean failure.", e);
            }
            moduleProvider.prepare();
        }
    }

    private void copyProperties(ModuleConfig dest, Properties src, String moduleName,
        String providerName) throws IllegalAccessException {
        if (dest == null) {
            return;
        }
        Enumeration<?> propertyNames = src.propertyNames();
        while (propertyNames.hasMoreElements()) {
            String propertyName = (String)propertyNames.nextElement();
            Class<? extends ModuleConfig> destClass = dest.getClass();

            try {
                Field field = getDeclaredField(destClass, propertyName);
                field.setAccessible(true);
                field.set(dest, src.get(propertyName));
            } catch (NoSuchFieldException e) {
                logger.warn(propertyName + " setting is not supported in " + providerName + " provider of " + moduleName + " module");
            }
        }
    }

    private Field getDeclaredField(Class<?> destClass, String fieldName) throws NoSuchFieldException {
        if (destClass != null) {
            Field[] fields = destClass.getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().equals(fieldName)) {
                    return field;
                }
            }
            return getDeclaredField(destClass.getSuperclass(), fieldName);
        }

        throw new NoSuchFieldException();
    }

    /**
     * @return providers of this module
     */
    final List<ModuleProvider> providers() {
        return loadedProviders;
    }

    @Override public final ModuleProvider provider() throws DuplicateProviderException, ProviderNotFoundException {
        if (loadedProviders.size() > 1) {
            throw new DuplicateProviderException(this.name() + " module exist " + loadedProviders.size() + " providers");
        } else if (loadedProviders.size() == 0) {
            throw new ProviderNotFoundException("There is no module provider in " + this.name() + " module!");
        }

        return loadedProviders.getFirst();
    }
}
