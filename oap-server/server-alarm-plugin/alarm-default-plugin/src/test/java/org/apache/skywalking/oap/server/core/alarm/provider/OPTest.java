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

package org.apache.skywalking.oap.server.core.alarm.provider;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class OPTest {
    @Test
    public void test() {
        assertTrue(OP.EQUAL.test(123, 123));
        assertTrue(OP.EQUAL.test(123L, 123L));
        assertTrue(OP.EQUAL.test(123.0D, 123.0D));

        assertTrue(OP.GREATER.test(122, 123));
        assertTrue(OP.GREATER.test(122L, 123L));
        assertTrue(OP.GREATER.test(122.0D, 123.0D));

        assertTrue(OP.GREATER_EQ.test(122, 123));
        assertTrue(OP.GREATER_EQ.test(122L, 123L));
        assertTrue(OP.GREATER_EQ.test(122.0D, 123.0D));

        assertTrue(OP.LESS.test(124, 123));
        assertTrue(OP.LESS.test(124L, 123L));
        assertTrue(OP.LESS.test(124.0D, 123.0D));
    }
}