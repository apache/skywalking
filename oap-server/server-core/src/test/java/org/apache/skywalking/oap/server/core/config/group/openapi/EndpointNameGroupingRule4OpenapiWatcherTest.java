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

import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

public class EndpointNameGroupingRule4OpenapiWatcherTest {
    @Test
    public void testWatcher() throws FileNotFoundException {
        EndpointNameGrouping endpointNameGrouping = new EndpointNameGrouping();

        EndpointNameGroupingRule4OpenapiWatcher watcher = new EndpointNameGroupingRule4OpenapiWatcher(
            new ModuleProvider() {
                @Override
                public String name() {
                    return "test";
                }

                @Override
                public Class<? extends ModuleDefine> module() {
                    return CoreModule.class;
                }

                @Override
                public ConfigCreator newConfigCreator() {
                    return null;
                }

                @Override
                public void prepare() throws ServiceNotProvidedException {

                }

                @Override
                public void start() throws ServiceNotProvidedException {

                }

                @Override
                public void notifyAfterCompleted() throws ServiceNotProvidedException {

                }

                @Override
                public String[] requiredModules() {
                    return new String[0];
                }
            }, endpointNameGrouping);
        Assertions.assertEquals("GET:/products/{id}", endpointNameGrouping.format("serviceA", "GET:/products/123"));

        Map<String, ConfigChangeWatcher.ConfigChangeEvent> groupItems = new HashMap<>();
        groupItems.put(
            "serviceA.productAPI-v1",
            new ConfigChangeWatcher
                .ConfigChangeEvent(
                "openapi: 3.0.0\n" +
                    "\n" +
                    "info:\n" +
                    "  description: OpenAPI definition for SkyWalking test.\n" +
                    "  version: v1\n" +
                    "  title: Product API\n" +
                    "\n" +
                    "tags:\n" +
                    "  - name: product\n" +
                    "    description: product\n" +
                    "  - name: relatedProducts\n" +
                    "    description: Related Products\n" +
                    "\n" +
                    "paths:\n" +
                    "  /products:\n" +
                    "    get:\n" +
                    "      tags:\n" +
                    "        - product\n" +
                    "      summary: Get all products list\n" +
                    "      description: Get all products list.\n" +
                    "      operationId: getProducts\n" +
                    "      responses:\n" +
                    "        \"200\":\n" +
                    "          description: Success\n" +
                    "          content:\n" +
                    "            application/json:\n" +
                    "              schema:\n" +
                    "                type: array\n" +
                    "                items:\n" +
                    "                  $ref: \"#/components/schemas/Product\"\n" +
                    "  /products/{order-id}:\n" + //modified from /products/{id}
                    "    get:\n" +
                    "      tags:\n" +
                    "        - product\n" +
                    "      summary: Get product details\n" +
                    "      description: Get product details with the given id.\n" +
                    "      operationId: getProduct\n" +
                    "      parameters:\n" +
                    "        - name: id\n" +
                    "          in: path\n" +
                    "          description: Product id\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
                    "            format: int64\n" +
                    "      responses:\n" +
                    "        \"200\":\n" +
                    "          description: successful operation\n" +
                    "          content:\n" +
                    "            application/json:\n" +
                    "              schema:\n" +
                    "                $ref: \"#/components/schemas/ProductDetails\"\n" +
                    "        \"400\":\n" +
                    "          description: Invalid product id\n" +
                    "    post:\n" +
                    "      tags:\n" +
                    "        - product\n" +
                    "      summary: Update product details\n" +
                    "      description: Update product details with the given id.\n" +
                    "      operationId: updateProduct\n" +
                    "      parameters:\n" +
                    "        - name: id\n" +
                    "          in: path\n" +
                    "          description: Product id\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
                    "            format: int64\n" +
                    "        - name: name\n" +
                    "          in: query\n" +
                    "          description: Product name\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: string\n" +
                    "      responses:\n" +
                    "        \"200\":\n" +
                    "          description: successful operation\n" +
                    "    delete:\n" +
                    "      tags:\n" +
                    "        - product\n" +
                    "      summary: Delete product details\n" +
                    "      description: Delete product details with the given id.\n" +
                    "      operationId: deleteProduct\n" +
                    "      parameters:\n" +
                    "        - name: id\n" +
                    "          in: path\n" +
                    "          description: Product id\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
                    "            format: int64\n" +
                    "      responses:\n" +
                    "        \"200\":\n" +
                    "          description: successful operation\n" +
                    "  /products/{id}/relatedProducts:\n" +
                    "    get:\n" +
                    "      tags:\n" +
                    "        - relatedProducts\n" +
                    "      summary: Get related products\n" +
                    "      description: Get related products with the given product id.\n" +
                    "      operationId: getRelatedProducts\n" +
                    "      parameters:\n" +
                    "        - name: id\n" +
                    "          in: path\n" +
                    "          description: Product id\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
                    "            format: int64\n" +
                    "      responses:\n" +
                    "        \"200\":\n" +
                    "          description: successful operation\n" +
                    "          content:\n" +
                    "            application/json:\n" +
                    "              schema:\n" +
                    "                $ref: \"#/components/schemas/RelatedProducts\"\n" +
                    "        \"400\":\n" +
                    "          description: Invalid product id\n" +
                    "\n" +
                    "components:\n" +
                    "  schemas:\n" +
                    "    Product:\n" +
                    "      type: object\n" +
                    "      description: Product id and name\n" +
                    "      properties:\n" +
                    "        id:\n" +
                    "          type: integer\n" +
                    "          format: int64\n" +
                    "          description: Product id\n" +
                    "        name:\n" +
                    "          type: string\n" +
                    "          description: Product name\n" +
                    "      required:\n" +
                    "        - id\n" +
                    "        - name\n" +
                    "    ProductDetails:\n" +
                    "      type: object\n" +
                    "      description: Product details\n" +
                    "      properties:\n" +
                    "        id:\n" +
                    "          type: integer\n" +
                    "          format: int64\n" +
                    "          description: Product id\n" +
                    "        name:\n" +
                    "          type: string\n" +
                    "          description: Product name\n" +
                    "        description:\n" +
                    "          type: string\n" +
                    "          description: Product description\n" +
                    "      required:\n" +
                    "        - id\n" +
                    "        - name\n" +
                    "    RelatedProducts:\n" +
                    "      type: object\n" +
                    "      description: Related Products\n" +
                    "      properties:\n" +
                    "        id:\n" +
                    "          type: integer\n" +
                    "          format: int32\n" +
                    "          description: Product id\n" +
                    "        relatedProducts:\n" +
                    "          type: array\n" +
                    "          description: List of related products\n" +
                    "          items:\n" +
                    "            $ref: \"#/components/schemas/Product\"",
                ConfigChangeWatcher.EventType.MODIFY
            )
        );

        watcher.notifyGroup(groupItems);
        Assertions.assertEquals("GET:/products/{order-id}", endpointNameGrouping.format("serviceA", "GET:/products/123"));

        groupItems.put("serviceA.productAPI-v1", new ConfigChangeWatcher.ConfigChangeEvent("", ConfigChangeWatcher.EventType.DELETE));
        watcher.notifyGroup(groupItems);

        Assertions.assertEquals("GET:/products/123", endpointNameGrouping.format("serviceA", "GET:/products/123"));

    }
}
