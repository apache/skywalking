/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.core.analysis.metrics.expression;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NumberMatchTest {

    @Test
    public void integerShouldEqual() {
        Integer a = 334;
        Integer b = 334;
        boolean match = new NumberMatch().match(a, b);
        assertTrue(match);

        a = -123;
        b = -123;
        match = new NumberMatch().match(a, b);
        assertTrue(match);

        a = -122;
        b = -123;
        match = new NumberMatch().match(a, b);
        assertFalse(match);

        a = -123;
        b = -122;
        match = new NumberMatch().match(a, b);
        assertFalse(match);
    }

    @Test
    public void intShouldEqual() {
        int a = 334;
        int b = 334;
        boolean match = new NumberMatch().match(a, b);
        assertTrue(match);

        a = -123;
        b = -123;
        match = new NumberMatch().match(a, b);
        assertTrue(match);

        a = -122;
        b = -123;
        match = new NumberMatch().match(a, b);
        assertFalse(match);

        a = -123;
        b = -122;
        match = new NumberMatch().match(a, b);
        assertFalse(match);
    }

    @Test
    public void longShouldEqual() {
        long a = 21474836478L;
        long b = 21474836478L;
        boolean match = new NumberMatch().match(a, b);
        assertTrue(match);

        a = -21474836478L;
        b = -21474836479L;
        match = new NumberMatch().match(a, b);
        assertFalse(match);

        Long c = -123L;
        Long d = -123L;
        match = new NumberMatch().match(c, d);
        assertTrue(match);

        c = -21474836478L;
        d = -21474836479L;
        match = new NumberMatch().match(c, d);
        assertFalse(match);
    }

}