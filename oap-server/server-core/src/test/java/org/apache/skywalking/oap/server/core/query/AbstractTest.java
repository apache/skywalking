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

package org.apache.skywalking.oap.server.core.query;

import org.apache.skywalking.oap.server.core.cache.EndpointInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.EndpointInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by dengming in 2019-05-17
 */
public abstract class AbstractTest {

    protected static final String SERVICE_INVENTORY_NAME = "service-inventory";
    protected static final String SERVICE_INSTANCE_INVENTORY_NAME = "service-instance-inventory";
    protected static final String ENDPOINT_INVENTORY_NAME = "endpoint-inventory";

    protected ServiceInventoryCache serviceInventoryCache = mock(ServiceInventoryCache.class);
    protected ServiceInstanceInventoryCache serviceInstanceInventoryCache = mock(ServiceInstanceInventoryCache.class);
    protected EndpointInventoryCache endpointInventoryCache = mock(EndpointInventoryCache.class);


    protected ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);

    protected ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);

    protected ModuleManager moduleManager = mock(ModuleManager.class);


    protected ServiceInventory serviceInventory = mock(ServiceInventory.class);
    protected ServiceInstanceInventory serviceInstanceInventory = mock(ServiceInstanceInventory.class);
    protected EndpointInventory endpointInventory = mock(EndpointInventory.class);


    public AbstractTest() {
        // mock ValueColumnIds

        // mock serviceInventory
        when(serviceInventory.getName()).thenReturn(SERVICE_INVENTORY_NAME);

        //mock serviceInstanceInventory
        when(serviceInstanceInventory.getName()).thenReturn(SERVICE_INSTANCE_INVENTORY_NAME);

        //mock endpointInventory
        when(endpointInventory.getName()).thenReturn(ENDPOINT_INVENTORY_NAME);

        //mock service inventory cache
        when(serviceInventoryCache.get(anyInt())).thenReturn(serviceInventory);

        //mock service instance inventory cache
        when(serviceInstanceInventoryCache.get(anyInt())).thenReturn(serviceInstanceInventory);

        //mock endpoint inventory cache
        when(endpointInventoryCache.get(anyInt())).thenReturn(endpointInventory);

        //mock moduleServiceHolder
        when(moduleServiceHolder.getService(ServiceInventoryCache.class)).thenReturn(serviceInventoryCache);
        when(moduleServiceHolder.getService(ServiceInstanceInventoryCache.class)).thenReturn(serviceInstanceInventoryCache);
        when(moduleServiceHolder.getService(EndpointInventoryCache.class)).thenReturn(endpointInventoryCache);

        //mock moduleServiceHolder
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);

        //mock moduleManager
        when(moduleManager.find(anyString())).thenReturn(moduleProviderHolder);
    }

}
