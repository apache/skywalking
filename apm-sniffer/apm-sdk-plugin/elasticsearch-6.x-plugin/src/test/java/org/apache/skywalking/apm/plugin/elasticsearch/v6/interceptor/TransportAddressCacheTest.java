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

package org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor;

import org.apache.skywalking.apm.plugin.elasticsearch.v6.TransportAddressCache;
import org.elasticsearch.common.transport.TransportAddress;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class TransportAddressCacheTest {

    private TransportAddressCache transportAddressCache;

    @Before
    public void setUp() {
        transportAddressCache = new TransportAddressCache();
    }

    @Test
    public void transportAddressTest()
        throws UnknownHostException {

        transportAddressCache.addDiscoveryNode(
            new TransportAddress(InetAddress.getByName("172.1.1.1"), 9300),
            new TransportAddress(InetAddress.getByName("172.1.1.2"), 9200),
            new TransportAddress(InetAddress.getByName("172.1.1.3"), 9100)
        );

        assertThat(transportAddressCache.transportAddress(), is("172.1.1.1:9300,172.1.1.2:9200,172.1.1.3:9100"));
    }

}
