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

package org.apache.skywalking.oap.server.library.batchqueue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThreadPolicyTest {

    @Test
    public void testFixedReturnsExactCount() {
        assertEquals(1, ThreadPolicy.fixed(1).resolve());
        assertEquals(4, ThreadPolicy.fixed(4).resolve());
        assertEquals(100, ThreadPolicy.fixed(100).resolve());
    }

    @Test
    public void testFixedRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> ThreadPolicy.fixed(0));
    }

    @Test
    public void testFixedRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> ThreadPolicy.fixed(-1));
    }

    @Test
    public void testCpuCoresResolvesAtLeastOne() {
        // Even with a tiny multiplier, resolve should return >= 1
        assertTrue(ThreadPolicy.cpuCores(0.001).resolve() >= 1);
    }

    @Test
    public void testCpuCoresScalesWithProcessors() {
        final int cores = Runtime.getRuntime().availableProcessors();
        final int resolved = ThreadPolicy.cpuCores(1.0).resolve();
        assertEquals(cores, resolved);
    }

    @Test
    public void testCpuCoresRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> ThreadPolicy.cpuCores(0));
    }

    @Test
    public void testCpuCoresRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> ThreadPolicy.cpuCores(-0.5));
    }

    @Test
    public void testToStringFixed() {
        assertEquals("fixed(4)", ThreadPolicy.fixed(4).toString());
    }

    @Test
    public void testToStringCpuCores() {
        assertEquals("cpuCores(0.5)", ThreadPolicy.cpuCores(0.5).toString());
    }

    @Test
    public void testCpuCoresWithBaseAddsBaseToScaled() {
        final int cores = Runtime.getRuntime().availableProcessors();
        final int resolved = ThreadPolicy.cpuCoresWithBase(2, 0.25).resolve();
        assertEquals(2 + (int) Math.round(0.25 * cores), resolved);
    }

    @Test
    public void testCpuCoresWithBaseResolvesAtLeastOne() {
        assertTrue(ThreadPolicy.cpuCoresWithBase(0, 0.001).resolve() >= 1);
    }

    @Test
    public void testCpuCoresWithBaseRejectsNegativeBase() {
        assertThrows(IllegalArgumentException.class, () -> ThreadPolicy.cpuCoresWithBase(-1, 0.25));
    }

    @Test
    public void testCpuCoresWithBaseRejectsZeroMultiplier() {
        assertThrows(IllegalArgumentException.class, () -> ThreadPolicy.cpuCoresWithBase(2, 0));
    }

    @Test
    public void testCpuCoresWithBaseRejectsNegativeMultiplier() {
        assertThrows(IllegalArgumentException.class, () -> ThreadPolicy.cpuCoresWithBase(2, -0.5));
    }

    @Test
    public void testToStringCpuCoresWithBase() {
        assertEquals("cpuCoresWithBase(2, 0.25)", ThreadPolicy.cpuCoresWithBase(2, 0.25).toString());
    }
}
