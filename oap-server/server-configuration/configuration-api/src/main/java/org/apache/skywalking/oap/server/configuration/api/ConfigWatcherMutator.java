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

package org.apache.skywalking.oap.server.configuration.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.apache.skywalking.oap.server.library.module.ModuleConfigMutator;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

public interface ConfigWatcherMutator extends ModuleConfigMutator {
    /**
     * mutate the propertyValue to Dynamic Watcher which is marked with @AsWatcher
     * The filed type class must have appropriate constructor method , which signature is
     * "(String itermName , String config, ModuleProvider provider)"
     */
    @Override
    default Object mutate(final ModuleDefine currentModule,
                          final Field field,
                          final String propertyKey,
                          final Object propertyValue) {
        if (!(propertyValue instanceof String)) {
            return null;
        }

        Class<?> fieldClass = field.getType();
        if (!ConfigChangeWatcher.class.isAssignableFrom(fieldClass)) {
            return null;
        }
        final AsWatcher annotation = field.getAnnotation(AsWatcher.class);
        if (annotation == null) {
            return null;
        }
        String itermName = annotation.itermName().isEmpty() ? propertyKey : annotation.itermName();
        String value = (String) propertyValue;
        try {
            final Constructor<?> constructor = fieldClass.getConstructor(
                String.class, String.class, ModuleProvider.class);
            return constructor.newInstance(itermName, value, currentModule.provider());
        } catch (Exception e) {
            //ignore exception
        }
        return null;
    }

    /**
     * Register this holder object field which is marked with @AsWatcher
     */
    default void registerMarkedWatcherTo(DynamicConfigurationService service) {
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.getAnnotation(AsWatcher.class) != null) {
                field.setAccessible(true);
                try {
                    service.registerConfigChangeWatcher((ConfigChangeWatcher) field.get(this));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
