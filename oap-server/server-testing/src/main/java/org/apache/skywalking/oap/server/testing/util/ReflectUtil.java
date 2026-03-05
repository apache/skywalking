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
 */

package org.apache.skywalking.oap.server.testing.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection utilities for test code. Replaces {@code org.powermock.reflect.Whitebox}.
 */
public final class ReflectUtil {

    private ReflectUtil() {
    }

    /**
     * Set a field value on an object instance, searching up the class hierarchy.
     */
    public static void setInternalState(final Object target, final String fieldName,
                                        final Object value) {
        final Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field '" + fieldName + "'", e);
        }
    }

    /**
     * Set a static field value on a class, searching up the class hierarchy.
     */
    public static void setInternalState(final Class<?> clazz, final String fieldName,
                                        final Object value) {
        final Field field = findField(clazz, fieldName);
        field.setAccessible(true);
        try {
            field.set(null, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set static field '" + fieldName + "'", e);
        }
    }

    /**
     * Get a field value from an object instance, searching up the class hierarchy.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getInternalState(final Object target, final String fieldName) {
        final Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        try {
            return (T) field.get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get field '" + fieldName + "'", e);
        }
    }

    /**
     * Invoke a method on an object instance by name, searching up the class hierarchy.
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(final Object target, final String methodName,
                                     final Object... args) throws Exception {
        final Class<?>[] paramTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
        }
        final Method method = findMethod(target.getClass(), methodName, paramTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }

    private static Field findField(final Class<?> clazz, final String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException(
            "Field '" + fieldName + "' not found in " + clazz.getName() + " or its superclasses");
    }

    private static Method findMethod(final Class<?> clazz, final String methodName,
                                     final Class<?>[] paramTypes) {
        Class<?> current = clazz;
        while (current != null) {
            for (final Method m : current.getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) {
                    continue;
                }
                if (m.getParameterCount() != paramTypes.length) {
                    continue;
                }
                boolean match = true;
                final Class<?>[] declared = m.getParameterTypes();
                for (int i = 0; i < declared.length; i++) {
                    if (!declared[i].isAssignableFrom(paramTypes[i])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        throw new RuntimeException(
            "Method '" + methodName + "' not found in " + clazz.getName() + " or its superclasses");
    }
}
