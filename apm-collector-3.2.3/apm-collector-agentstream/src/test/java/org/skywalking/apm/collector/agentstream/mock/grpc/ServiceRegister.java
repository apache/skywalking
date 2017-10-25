/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentstream.mock.grpc;

import io.grpc.ManagedChannel;
import org.skywalking.apm.network.proto.ServiceNameCollection;
import org.skywalking.apm.network.proto.ServiceNameDiscoveryServiceGrpc;
import org.skywalking.apm.network.proto.ServiceNameElement;
import org.skywalking.apm.network.proto.ServiceNameMappingCollection;

/**
 * @author peng-yongsheng
 */
public class ServiceRegister {

    public static int register(ManagedChannel channel, int applicationId, String serviceName) {
        ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceBlockingStub stub = ServiceNameDiscoveryServiceGrpc.newBlockingStub(channel);
        ServiceNameCollection.Builder collection = ServiceNameCollection.newBuilder();

        ServiceNameElement.Builder element = ServiceNameElement.newBuilder();
        element.setApplicationId(applicationId);
        element.setServiceName(serviceName);
        collection.addElements(element);

        ServiceNameMappingCollection mappingCollection = stub.discovery(collection.build());
        int serviceId = mappingCollection.getElements(0).getServiceId();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }
        return serviceId;
    }
}
