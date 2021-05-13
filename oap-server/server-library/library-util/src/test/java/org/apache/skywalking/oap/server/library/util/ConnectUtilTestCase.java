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

import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class ConnectUtilTestCase {

    @Test
    public void parse() throws ConnectStringParseException {
        List<Address> list = ConnectUtils.parse("10.0.0.1:1000,10.0.0.2:1001");
        Assert.assertEquals(2, list.size());

        Assert.assertEquals("10.0.0.1", list.get(0).getHost());
        Assert.assertEquals(1000, list.get(0).getPort());

        Assert.assertEquals("10.0.0.2", list.get(1).getHost());
        Assert.assertEquals(1001, list.get(1).getPort());
    }

    @Test
    public void comma() throws ConnectStringParseException {
        List<Address> list = ConnectUtils.parse("10.0.0.1:1000,");

        Assert.assertEquals(1, list.size());

        Assert.assertEquals("10.0.0.1", list.get(0).getHost());
        Assert.assertEquals(1000, list.get(0).getPort());

        list = ConnectUtils.parse(",10.0.0.1:1000");

        Assert.assertEquals(1, list.size());

        Assert.assertEquals("10.0.0.1", list.get(0).getHost());
        Assert.assertEquals(1000, list.get(0).getPort());
    }

    @Test(expected = ConnectStringParseException.class)
    public void nullTest() throws ConnectStringParseException {
        List<Address> list = ConnectUtils.parse(null);
    }

    @Test(expected = ConnectStringParseException.class)
    public void emptyTest() throws ConnectStringParseException {
        List<Address> list = ConnectUtils.parse("");
    }

    @Test(expected = ConnectStringParseException.class)
    public void shouldThrowIfOnlyComma() throws ConnectStringParseException {
        List<Address> list = ConnectUtils.parse(",,");
    }

    @Test(expected = ConnectStringParseException.class)
    public void shouldThrowIfHostWithoutPort() throws ConnectStringParseException {
        List<Address> list = ConnectUtils.parse("localhost");
    }

    @Test(expected = ConnectStringParseException.class)
    public void shouldThrowIfPortIsNotNumber() throws ConnectStringParseException {
        List<Address> list = ConnectUtils.parse("localhost:what");
    }

    @Test(expected = ConnectStringParseException.class)
    public void invalidPattern1() throws ConnectStringParseException {
        List<Address> list = ConnectUtils.parse("10.0.0.1:");
    }

    @Test(expected = ConnectStringParseException.class)
    public void invalidPattern2() throws ConnectStringParseException {
        List<Address> list = ConnectUtils.parse("10.0.0.1:xx");
    }
}
