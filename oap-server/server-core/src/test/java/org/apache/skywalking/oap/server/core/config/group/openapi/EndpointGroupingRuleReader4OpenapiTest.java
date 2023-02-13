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

package org.apache.skywalking.oap.server.core.config.group.openapi;

import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class EndpointGroupingRuleReader4OpenapiTest {

    @Test
    public void testReadingRule() throws IOException {

        EndpointGroupingRuleReader4Openapi reader = new EndpointGroupingRuleReader4Openapi("openapi-definitions");
        EndpointGroupingRule4Openapi rule = reader.read();
        EndpointNameGrouping nameGrouping = new EndpointNameGrouping();
        nameGrouping.setEndpointGroupingRule4Openapi(rule);

        //default x-sw-service-name x-sw-endpoint-name-match-rule and x-sw-endpoint-name-format
        // test direct lookup
        String endpointName = nameGrouping.format("serviceA", "GET:/products");
        Assertions.assertEquals("GET:/products", endpointName);

        endpointName = nameGrouping.format("serviceA", "GET:/products/123");
        Assertions.assertEquals("GET:/products/{id}", endpointName);

        endpointName = nameGrouping.format("serviceA", "GET:/products/123/abc/ef");
        Assertions.assertEquals("GET:/products/123/abc/ef", endpointName);

        endpointName = nameGrouping.format("serviceA", "GET:/products/123/relatedProducts");
        Assertions.assertEquals("GET:/products/{id}/relatedProducts", endpointName);

        endpointName = nameGrouping.format("serviceA", "GET:/products/1/relatedProducts");
        Assertions.assertEquals("GET:/products/{id}/relatedProducts", endpointName);

        //test custom x-sw-service-name same x-sw-endpoint-name-match-rule and x-sw-endpoint-name-format
        endpointName = nameGrouping.format("serviceA-1", "POST:/customer");
        Assertions.assertEquals("POST:/customer", endpointName);

        endpointName = nameGrouping.format("serviceA-1", "<GET>:/customers/1");
        Assertions.assertEquals("<GET>:/customers/{id}", endpointName);

        //test different x-sw-endpoint-name-match-rule and x-sw-endpoint-name-format
        endpointName = nameGrouping.format("serviceB", "GET:/products");
        Assertions.assertEquals("/products:<GET>", endpointName);

        endpointName = nameGrouping.format("serviceB", "GET:/products/asia/cn");
        Assertions.assertEquals("/products/{region}/{country}:<GET>", endpointName);

        //test match priority, not match /products/{region}/{country}:<GET>
        endpointName = nameGrouping.format("serviceB", "GET:/products/12/relatedProducts");
        Assertions.assertEquals("/products/{id}/relatedProducts:<GET>", endpointName);

        //test not match, return the origin
        endpointName = nameGrouping.format("serviceA", "GET:/products/");
        Assertions.assertNotEquals("GET:/products", endpointName);

        endpointName = nameGrouping.format("serviceA", "GET:/products/123/");
        Assertions.assertEquals("GET:/products/123/", endpointName);

        endpointName = nameGrouping.format("serviceC", "GET:/products/123");
        Assertions.assertEquals("GET:/products/123", endpointName);

        endpointName = nameGrouping.format("serviceA", "GET:/products/1/ratings/123");
        Assertions.assertEquals("GET:/products/1/ratings/123", endpointName);

        endpointName = nameGrouping.format("serviceA-1", "<GET>:/customers/1/123");
        Assertions.assertEquals("<GET>:/customers/1/123", endpointName);

        endpointName = nameGrouping.format("serviceB", "/products/:<GET>");
        Assertions.assertEquals("/products/:<GET>", endpointName);

        endpointName = nameGrouping.format("serviceB", "{GET}:/products");
        Assertions.assertEquals("{GET}:/products", endpointName);

        endpointName = nameGrouping.format("serviceB", "/products/1/2/3:<GET>");
        Assertions.assertEquals("/products/1/2/3:<GET>", endpointName);

    }
}
