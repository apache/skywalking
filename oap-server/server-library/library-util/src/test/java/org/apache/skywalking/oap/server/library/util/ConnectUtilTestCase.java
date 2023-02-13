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

package org.apache.skywalking.oap.server.library.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConnectUtilTestCase {

    @Test
    public void parse() throws ConnectStringParseException {
        List<Address> list = ConnectUtils.parse("10.0.0.1:1000,10.0.0.2:1001");
        assertEquals(2, list.size());

        assertEquals("10.0.0.1", list.get(0).getHost());
        assertEquals(1000, list.get(0).getPort());

        assertEquals("10.0.0.2", list.get(1).getHost());
        assertEquals(1001, list.get(1).getPort());
    }

    @Test
    public void comma() throws ConnectStringParseException {
        List<Address> list = ConnectUtils.parse("10.0.0.1:1000,");

        assertEquals(1, list.size());

        assertEquals("10.0.0.1", list.get(0).getHost());
        assertEquals(1000, list.get(0).getPort());

        list = ConnectUtils.parse(",10.0.0.1:1000");

        assertEquals(1, list.size());

        assertEquals("10.0.0.1", list.get(0).getHost());
        assertEquals(1000, list.get(0).getPort());
    }

    @Test
    public void nullTest() {
        assertThrows(ConnectStringParseException.class, () -> ConnectUtils.parse(null));
    }

    @Test
    public void emptyTest() {
        assertThrows(ConnectStringParseException.class, () -> ConnectUtils.parse(""));
    }

    @Test
    public void shouldThrowIfOnlyComma() {
        assertThrows(ConnectStringParseException.class, () -> ConnectUtils.parse(",,"));
    }

    @Test
    public void shouldThrowIfHostWithoutPort() {
        assertThrows(ConnectStringParseException.class, () -> ConnectUtils.parse("localhost"));
    }

    @Test
    public void shouldThrowIfPortIsNotNumber() {
        assertThrows(ConnectStringParseException.class, () -> ConnectUtils.parse("localhost:what"));
    }

    @Test
    public void invalidPattern1() {
        assertThrows(ConnectStringParseException.class, () -> ConnectUtils.parse("10.0.0.1:"));
    }

    @Test
    public void invalidPattern2() {
        assertThrows(ConnectStringParseException.class, () -> ConnectUtils.parse("10.0.0.1:xx"));
    }
}
