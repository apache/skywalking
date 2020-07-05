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

package org.apache.skywalking.oap.server.configuration.etcd;

import java.net.URI;
import java.util.List;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestEtcdUtils {

    private EtcdServerSettings settings;

    private Properties properties;

    @Before
    public void setUp() {
        settings = new EtcdServerSettings();
        settings.setServerAddr("localhost:2379");
        properties = new Properties();
        properties.setProperty("serverAddr", "localhost:2379");
    }

    @Test
    public void testParse() {
        List<URI> list = EtcdUtils.parse(settings);
        Assert.assertEquals(1, list.size());
        URI uri = list.get(0);
        Assert.assertEquals("http", uri.getScheme());
        Assert.assertEquals("localhost", uri.getHost());
        Assert.assertEquals(2379, uri.getPort());
    }

    @Test
    public void testProp() {
        List<URI> list = EtcdUtils.parseProp(properties);
        Assert.assertEquals(1, list.size());
        URI uri = list.get(0);
        Assert.assertEquals("http", uri.getScheme());
        Assert.assertEquals("localhost", uri.getHost());
        Assert.assertEquals(2379, uri.getPort());
    }
}
