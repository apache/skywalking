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

package org.apache.skywalking.apm.agent.core.plugin;

import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.pool.TypePool;

/**
 * The <code>WitnessFinder</code> represents a pool of {@link TypePool}s, each {@link TypePool} matches a {@link
 * ClassLoader}, which helps to find the class define existed or not.
 */
public enum WitnessFinder {
    INSTANCE;

    private final Map<ClassLoader, TypePool> poolMap = new HashMap<ClassLoader, TypePool>();

    /**
     * @param classLoader for finding the witnessClass
     * @return true, if the given witnessClass exists, through the given classLoader.
     */
    public static boolean exist(String witnessClass, ClassLoader classLoader) {
        return getResolution(witnessClass, classLoader)
                .isResolved();
    }

    /**
     * get TypePool.Resolution of the witness class
     * @param witnessClass class name
     * @param classLoader classLoader for finding the witnessClass
     * @return TypePool.Resolution
     */
    private static TypePool.Resolution getResolution(String witnessClass, ClassLoader classLoader) {
        ClassLoader mappingKey = classLoader == null ? NullClassLoader.INSTANCE : classLoader;
        if (!INSTANCE.poolMap.containsKey(mappingKey)) {
            synchronized (INSTANCE.poolMap) {
                if (!INSTANCE.poolMap.containsKey(mappingKey)) {
                    TypePool classTypePool = classLoader == null ? TypePool.Default.ofBootLoader() : TypePool.Default.of(classLoader);
                    INSTANCE.poolMap.put(mappingKey, classTypePool);
                }
            }
        }
        TypePool typePool = INSTANCE.poolMap.get(mappingKey);
        return typePool.describe(witnessClass);
    }

    /**
     * @param classLoader for finding the witness method
     * @return true, if the given witness method exists, through the given classLoader.
     */
    public static boolean exist(WitnessMethod witnessMethod, ClassLoader classLoader) {
        TypePool.Resolution resolution = getResolution(witnessMethod.getDeclaringClassName(), classLoader);
        if (!resolution.isResolved()) {
            return false;
        }
        return !resolution.resolve()
                .getDeclaredMethods()
                .filter(witnessMethod.getElementMatcher())
                .isEmpty();
    }

}

final class NullClassLoader extends ClassLoader {
    static NullClassLoader INSTANCE = new NullClassLoader();
}
