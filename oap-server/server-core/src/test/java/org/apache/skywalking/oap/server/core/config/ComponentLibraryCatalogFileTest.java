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

package org.apache.skywalking.oap.server.core.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ComponentLibraryCatalogFileTest {
    @Test
    public void testInitAndSettings() {
        ComponentLibraryCatalogService service = new ComponentLibraryCatalogService();
        Assertions.assertEquals(1, service.getComponentId("Tomcat"));
        Assertions.assertEquals(7, service.getServerIdBasedOnComponent(30));
        Assertions.assertEquals(21, service.getServerIdBasedOnComponent(21));
        Assertions.assertEquals("Redis", service.getServerNameBasedOnComponent(30));
    }

    /**
     * Test priority sequence, TCP < TLS(TCP) < RPC < HTTP < HTTPS < SpringMVC
     */
    @Test
    public void testPriority() {
        ComponentLibraryCatalogService service = new ComponentLibraryCatalogService();
        Assertions.assertEquals(false, service.compare(service.getComponentId("Unknown"), service.getComponentId("tcp")));
        Assertions.assertEquals(false, service.compare(service.getComponentId("tcp"), service.getComponentId("tls")));
        Assertions.assertEquals(false, service.compare(service.getComponentId("tls"), service.getComponentId("rpc")));
        Assertions.assertEquals(false, service.compare(service.getComponentId("rpc"), service.getComponentId("http")));
        Assertions.assertEquals(false, service.compare(service.getComponentId("http"), service.getComponentId("https")));
        Assertions.assertEquals(false, service.compare(service.getComponentId("https"), service.getComponentId("SpringMVC")));

        // Equal priority
        Assertions.assertEquals(false, service.compare(service.getComponentId("Dubbo"), service.getComponentId("SpringMVC")));
        Assertions.assertEquals(false, service.compare(service.getComponentId("SpringMVC"), service.getComponentId("Dubbo")));
    }
}
