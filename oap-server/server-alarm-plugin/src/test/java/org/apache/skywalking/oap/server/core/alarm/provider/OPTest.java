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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class OPTest {
    @Test
    public void test() {
        assertTrue(OP.EQ.test(123, 123));
        assertTrue(OP.EQ.test(123L, 123L));
        assertTrue(OP.EQ.test(123.0D, 123.0D));

        assertTrue(OP.NEQ.test(124, 123));
        assertTrue(OP.NEQ.test(124L, 123L));
        assertTrue(OP.NEQ.test(124.0D, 123.0D));

        assertTrue(OP.GT.test(122, 123));
        assertTrue(OP.GT.test(122L, 123L));
        assertTrue(OP.GT.test(122.0D, 123.0D));

        assertTrue(OP.GTE.test(122, 123));
        assertTrue(OP.GTE.test(122L, 123L));
        assertTrue(OP.GTE.test(122.0D, 123.0D));
        assertTrue(OP.GTE.test(122, 122));
        assertTrue(OP.GTE.test(122L, 122L));
        assertTrue(OP.GTE.test(122.0D, 122.0D));

        assertTrue(OP.LT.test(124, 123));
        assertTrue(OP.LT.test(124L, 123L));
        assertTrue(OP.LT.test(124.0D, 123.0D));

        assertTrue(OP.LTE.test(124, 124));
        assertTrue(OP.LTE.test(124L, 124L));
        assertTrue(OP.LTE.test(124.0D, 124.0D));
        assertTrue(OP.LTE.test(124, 123));
        assertTrue(OP.LTE.test(124L, 123L));
        assertTrue(OP.LTE.test(124.0D, 123.0D));
    }
}
