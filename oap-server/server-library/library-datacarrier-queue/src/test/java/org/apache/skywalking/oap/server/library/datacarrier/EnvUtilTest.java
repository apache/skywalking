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

package org.apache.skywalking.oap.server.library.datacarrier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SystemStubsExtension.class)
public class EnvUtilTest {
    @SystemStub
    private final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @BeforeEach
    public void before() {
        environmentVariables.set("myInt", "123");
        environmentVariables.set("wrongInt", "wrong123");
        environmentVariables.set("myLong", "12345678901234567");
        environmentVariables.set("wrongLong", "wrong123");
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
