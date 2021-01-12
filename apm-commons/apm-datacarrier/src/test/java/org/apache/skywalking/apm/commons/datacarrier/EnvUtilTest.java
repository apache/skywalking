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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(EnvUtil.class)
public class EnvUtilTest {

    @Before
    public void before() {

        PowerMockito.mockStatic(System.class);

        when(System.getenv("myInt")).thenReturn("123");
        when(System.getenv("wrongInt")).thenReturn("wrong123");
        when(System.getenv("myLong")).thenReturn("12345678901234567");
        when(System.getenv("wrongLong")).thenReturn("wrong123");
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

}