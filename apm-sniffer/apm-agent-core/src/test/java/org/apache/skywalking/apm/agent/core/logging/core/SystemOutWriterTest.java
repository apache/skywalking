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

package org.apache.skywalking.apm.agent.core.logging.core;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.PrintStream;

import static org.mockito.Matchers.anyString;

public class SystemOutWriterTest {
    private static PrintStream OUT_REF;

    @BeforeClass
    public static void initAndHoldOut() {
        OUT_REF = System.out;
    }

    @Test
    public void testWrite() {
        PrintStream mockStream = Mockito.mock(PrintStream.class);
        System.setOut(mockStream);

        SystemOutWriter.INSTANCE.write("hello");

        Mockito.verify(mockStream).println(anyString());
    }

    @AfterClass
    public static void reset() {
        System.setOut(OUT_REF);
    }
}
