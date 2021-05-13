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

package org.apache.skywalking.oap.server.cluster.plugin.zookeeper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.InstanceSerializer;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;

public class SWInstanceSerializer implements InstanceSerializer<RemoteInstance> {

    private final Gson gson = new Gson();

    @Override
    public byte[] serialize(ServiceInstance<RemoteInstance> instance) throws Exception {
        return gson.toJson(instance).getBytes();
    }

    @Override
    public ServiceInstance<RemoteInstance> deserialize(byte[] bytes) throws Exception {
        return gson.fromJson(new String(bytes), new TypeToken<ServiceInstance<RemoteInstance>>() {
        }.getType());
    }
}
