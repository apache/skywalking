/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public final class SequenceGeneratorTest {
    @Test
    public void testSequence() {
        SequenceGenerator generator =
            new SequenceGenerator.Builder()
                .setMin(1L)
                .setMax(100L)
                .build();

        for (int i = 0; i < 10; i++) {
            assertEquals(i + 1, generator.next().intValue());
        }
    }

    @Test
    public void testFluctuation() {
        SequenceGenerator generator =
            new SequenceGenerator.Builder()
                .setMin(1L)
                .setMax(100L)
                .setFluctuation(1)
                .build();

        for (int i = 1; i < 10; i++) {
            Long next = generator.next();
            assertTrue(i <= next.intValue());
            assertTrue(i * 2 >= next.intValue());
        }
    }
}
