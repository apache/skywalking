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

package org.apache.skywalking.apm.commons.datacarrier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.ConcurrentModificationException;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author dengming
 * 2019-04-20
 */
public class EnvUtilTest {


    @Before
    public void before() {
        setenv("myInt", "123");
        setenv("wrongInt", "wrong123");
        setenv("myLong", "12345678901234567");
        setenv("wrongLong", "wrong123");
    }


    /**
     * Sets an environment variable FOR THE CURRENT RUN OF THE JVM
     * Does not actually modify the system's environment variables,
     *  but rather only the copy of the variables that java has taken,
     *  and hence should only be used for testing purposes!
     * @param key The Name of the variable to set
     * @param value The value of the variable to set
     */
    @SuppressWarnings("unchecked")
    public static <K,V> void setenv(final String key, final String value) {
        try {
            /// we obtain the actual environment
            final Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            final Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            final boolean environmentAccessibility = theEnvironmentField.isAccessible();
            theEnvironmentField.setAccessible(true);

            final Map<K,V> env = (Map<K, V>) theEnvironmentField.get(null);

            String osName = System.getProperty("os.name");

            if (osName.contains("Windows")) {
                // This is all that is needed on windows running java jdk 1.8.0_92
                if (value == null) {
                    env.remove(key);
                } else {
                    env.put((K) key, (V) value);
                }
            } else {
                // This is triggered to work on openjdk 1.8.0_91
                // The ProcessEnvironment$Variable is the key of the map
                final Class<K> variableClass = (Class<K>) Class.forName("java.lang.ProcessEnvironment$Variable");
                final Method convertToVariable = variableClass.getMethod("valueOf", String.class);
                final boolean conversionVariableAccessibility = convertToVariable.isAccessible();
                convertToVariable.setAccessible(true);

                // The ProcessEnvironment$Value is the value fo the map
                final Class<V> valueClass = (Class<V>) Class.forName("java.lang.ProcessEnvironment$Value");
                final Method convertToValue = valueClass.getMethod("valueOf", String.class);
                final boolean conversionValueAccessibility = convertToValue.isAccessible();
                convertToValue.setAccessible(true);

                if (value == null) {
                    env.remove(convertToVariable.invoke(null, key));
                } else {
                    // we place the new value inside the map after conversion so as to
                    // avoid class cast exceptions when rerunning this code
                    env.put((K) convertToVariable.invoke(null, key), (V) convertToValue.invoke(null, value));

                    // reset accessibility to what they were
                    convertToValue.setAccessible(conversionValueAccessibility);
                    convertToVariable.setAccessible(conversionVariableAccessibility);
                }
            }
            // reset environment accessibility
            theEnvironmentField.setAccessible(environmentAccessibility);

            // we apply the same to the case insensitive environment
            final Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            final boolean insensitiveAccessibility = theCaseInsensitiveEnvironmentField.isAccessible();
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            // Not entirely sure if this needs to be casted to ProcessEnvironment$Variable and $Value as well
            final Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
            if (value == null) {
                // remove if null
                cienv.remove(key);
            } else {
                cienv.put(key, value);
            }
            theCaseInsensitiveEnvironmentField.setAccessible(insensitiveAccessibility);
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Failed setting environment variable <" + key + "> to <" + value + ">", e);
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException("Failed setting environment variable <" + key + "> to <" + value + ">", e);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException("Failed setting environment variable <" + key + "> to <" + value + ">", e);
        } catch (final InvocationTargetException e) {
            throw new IllegalStateException("Failed setting environment variable <" + key + "> to <" + value + ">", e);
        } catch (final NoSuchFieldException e) {
            // we could not find theEnvironment
            final Map<String, String> env = System.getenv();
            try {
                Class<?> cl = env.getClass();
                Field field = cl.getDeclaredField("m");
                final boolean fieldAccessibility = field.isAccessible();
                field.setAccessible(true);
                // we obtain the environment
                final Map<String, String> map = (Map<String, String>) field.get(env);
                if (value == null) {
                    // remove if null
                    map.remove(key);
                } else {
                    map.put(key, value);
                }
                // reset accessibility
                field.setAccessible(fieldAccessibility);
            } catch (final ConcurrentModificationException e1) {
                // This may happen if we keep backups of the environment before calling this method
                // as the map that we kept as a backup may be picked up inside this block.
                // So we simply skip this attempt and continue adjusting the other maps
                // To avoid this one should always keep individual keys/value backups not the entire map
                throw new IllegalStateException("Failed setting environment variable <" + key +
                        "> to <" + value + ">. Unable to access field!", e1);

            } catch (final IllegalAccessException e1) {
                throw new IllegalStateException("Failed setting environment variable <" + key +
                        "> to <" + value + ">. Unable to access field!", e1);
            } catch (NoSuchFieldException ex) {
                throw new IllegalStateException("Failed setting environment variable <" + key +
                        "> to <" + value + ">. Unable to access field!", ex);
            }

        }

    }

    @Test
    public void getInt() {
        assertEquals(123, EnvUtil.getInt("myInt", 234));
        assertEquals(234, EnvUtil.getLong("wrongInt", 234));
    }

    @Test
    public void getLong() {
        assertEquals(12345678901234567L, EnvUtil.getLong("myLong", 123L));
        assertEquals(987654321987654321L, EnvUtil.getLong("wrongLong", 987654321987654321L));
    }

    @After
    public void after() {
        setenv("myInt", null);
        setenv("wrongInt", null);
        setenv("myLong", null);
        setenv("wrongLong", null);
    }


}