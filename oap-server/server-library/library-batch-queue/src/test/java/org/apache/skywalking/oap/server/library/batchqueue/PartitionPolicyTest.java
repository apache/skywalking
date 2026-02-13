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

public class PartitionPolicyTest {

    @Test
    public void testFixedResolve() {
        // Returns exact count, ignores both threadCount and handlerCount
        assertEquals(1, PartitionPolicy.fixed(1).resolve(4, 0));
        assertEquals(8, PartitionPolicy.fixed(8).resolve(4, 0));
        assertEquals(4, PartitionPolicy.fixed(4).resolve(8, 500));

        // Same result regardless of threadCount
        final PartitionPolicy policy = PartitionPolicy.fixed(5);
        assertEquals(5, policy.resolve(1, 0));
        assertEquals(5, policy.resolve(100, 0));
    }

    @Test
    public void testFixedRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> PartitionPolicy.fixed(0));
    }

    @Test
    public void testFixedRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> PartitionPolicy.fixed(-1));
    }

    @Test
    public void testThreadMultiplyResolve() {
        // multiplier * threadCount, ignores handlerCount
        assertEquals(8, PartitionPolicy.threadMultiply(2).resolve(4, 0));
        assertEquals(12, PartitionPolicy.threadMultiply(3).resolve(4, 0));
        assertEquals(16, PartitionPolicy.threadMultiply(2).resolve(8, 500));

        // Even with 0 thread count, should return at least 1
        assertEquals(1, PartitionPolicy.threadMultiply(1).resolve(0, 0));
    }

    @Test
    public void testThreadMultiplyRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> PartitionPolicy.threadMultiply(0));
    }

    @Test
    public void testAdaptiveZeroHandlersReturnsThreadCount() {
        assertEquals(8, PartitionPolicy.adaptive().resolve(8, 0));
        assertEquals(4, PartitionPolicy.adaptive().resolve(4, 0));
        assertEquals(1, PartitionPolicy.adaptive().resolve(0, 0));
    }

    @Test
    public void testAdaptiveBelowThreshold() {
        // 8 threads * 25 = 200 threshold, handlerCount <= 200 -> 1:1
        assertEquals(50, PartitionPolicy.adaptive().resolve(8, 50));
        assertEquals(100, PartitionPolicy.adaptive().resolve(8, 100));
        assertEquals(200, PartitionPolicy.adaptive().resolve(8, 200));
    }

    @Test
    public void testAdaptiveAboveThreshold() {
        // 8 threads * 25 = 200 threshold, excess at 1:2 ratio
        assertEquals(350, PartitionPolicy.adaptive().resolve(8, 500));   // 200 + 300/2
        assertEquals(600, PartitionPolicy.adaptive().resolve(8, 1000));  // 200 + 800/2
        assertEquals(1100, PartitionPolicy.adaptive().resolve(8, 2000)); // 200 + 1800/2
    }

    @Test
    public void testAdaptiveCustomMultiplier() {
        // 8 threads * 50 = 400 threshold
        assertEquals(100, PartitionPolicy.adaptive(50).resolve(8, 100));  // 1:1
        assertEquals(400, PartitionPolicy.adaptive(50).resolve(8, 400));  // 1:1 at threshold
        assertEquals(500, PartitionPolicy.adaptive(50).resolve(8, 600));  // 400 + 200/2
    }

    @Test
    public void testAdaptiveWithDifferentThreadCounts() {
        // 4 threads * 25 = 100 threshold
        assertEquals(50, PartitionPolicy.adaptive().resolve(4, 50));     // 1:1
        assertEquals(100, PartitionPolicy.adaptive().resolve(4, 100));   // 1:1
        assertEquals(350, PartitionPolicy.adaptive().resolve(4, 600));   // 100 + 500/2
    }

    @Test
    public void testAdaptiveRejectsInvalidMultiplier() {
        assertThrows(IllegalArgumentException.class,
            () -> PartitionPolicy.adaptive(0));
    }

    @Test
    public void testToString() {
        assertEquals("fixed(4)", PartitionPolicy.fixed(4).toString());
        assertEquals("threadMultiply(2)", PartitionPolicy.threadMultiply(2).toString());
        assertEquals("adaptive(multiplier=25)", PartitionPolicy.adaptive().toString());
        assertEquals("adaptive(multiplier=50)", PartitionPolicy.adaptive(50).toString());
    }
}
