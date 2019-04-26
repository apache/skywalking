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
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author dengming
 * 2019-04-20
 */
public class EnvUtilTest {

    private Map<String, String> writableEnv;
    @Before
    public void before() {
        try {
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            writableEnv = (Map<String, String>) field.get(env);
            writableEnv.put("myInt", "123");
            writableEnv.put("wrongInt", "wrong123");
            writableEnv.put("myLong", "12345678901234567");
            writableEnv.put("wrongLong", "wrong123");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set environment variable", e);
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
        writableEnv.remove("myInt");
        writableEnv.remove("wrongInt");
        writableEnv.remove("myLong");
        writableEnv.remove("wrongLong");
        assertNull(System.getenv("myInt"));
    }


}