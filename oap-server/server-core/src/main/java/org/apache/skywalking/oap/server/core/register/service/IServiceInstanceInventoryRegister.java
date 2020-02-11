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

package org.apache.skywalking.oap.server.core.register.service;

import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.core.register.NodeType;
import org.apache.skywalking.oap.server.library.module.Service;

public interface IServiceInstanceInventoryRegister extends Service {

    int getOrCreate(int serviceId, String serviceInstanceName, String uuid, long registerTime, JsonObject properties);

    int getOrCreate(int serviceId, String serviceInstanceName, int addressId, long registerTime);

    void update(int serviceInstanceId, NodeType nodeType, JsonObject properties);

    void heartbeat(int serviceInstanceId, long heartBeatTime);

    void updateMapping(int serviceInstanceId, int mappingServiceInstanceId);

    /**
     * Reset the {@link org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory#mappingServiceInstanceId}
     * of a given service id.
     * <p>
     * There are cases when the mapping service id needs to be reset to {@code 0}, for example, when an uninstrumented
     * gateway joins, the mapping service id of the services that are delegated by this gateway should be reset to
     * {@code 0}, allowing the gateway to appear in the topology, see #3308 for more detail.
     *
     * @param serviceInstanceId id of the service whose mapping service id is to be reset
     */
    void resetMapping(int serviceInstanceId);
}
