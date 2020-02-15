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

package org.apache.skywalking.oap.server.receiver.trace.mock;

import io.grpc.ManagedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.network.common.KeyIntValuePair;
import org.apache.skywalking.apm.network.common.KeyStringValuePair;
import org.apache.skywalking.apm.network.common.ServiceType;
import org.apache.skywalking.apm.network.register.v2.RegisterGrpc;
import org.apache.skywalking.apm.network.register.v2.Service;
import org.apache.skywalking.apm.network.register.v2.ServiceInstance;
import org.apache.skywalking.apm.network.register.v2.ServiceInstanceRegisterMapping;
import org.apache.skywalking.apm.network.register.v2.ServiceInstances;
import org.apache.skywalking.apm.network.register.v2.ServiceRegisterMapping;
import org.apache.skywalking.apm.network.register.v2.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RegisterMock {

    private static final Logger logger = LoggerFactory.getLogger(RegisterMock.class);

    private final RegisterGrpc.RegisterBlockingStub registerStub;

    RegisterMock(ManagedChannel channel) {
        registerStub = RegisterGrpc.newBlockingStub(channel);
    }

    int registerService(String serviceName) throws InterruptedException {
        Services.Builder services = Services.newBuilder();
        services.addServices(Service
                                 .newBuilder()
                                 .setServiceName(serviceName)
                                 .setType(ServiceType.normal))
                .build();

        ServiceRegisterMapping serviceRegisterMapping;
        int serviceId = 0;
        do {
            serviceRegisterMapping = registerStub.doServiceRegister(services.build());

            List<KeyIntValuePair> servicesList = serviceRegisterMapping.getServicesList();
            if (servicesList.size() > 0) {
                serviceId = servicesList.get(0).getValue();
                logger.debug("service id: {}", serviceId);
            }

            TimeUnit.MILLISECONDS.sleep(20);
        }
        while (serviceId == 0);

        return serviceId;
    }

    int registerServiceInstance(int serviceId, String agentName) throws InterruptedException {
        ServiceInstances.Builder instances = ServiceInstances.newBuilder();

        instances.addInstances(ServiceInstance.newBuilder()
                                              .setServiceId(serviceId)
                                              .setInstanceUUID(agentName)
                                              .setTime(System.currentTimeMillis())
                                              .addAllProperties(buildOSInfo())
        );

        ServiceInstanceRegisterMapping instanceMapping;
        int instanceId = 0;
        do {
            instanceMapping = registerStub.doServiceInstanceRegister(instances.build());
            List<KeyIntValuePair> serviceInstancesList = instanceMapping.getServiceInstancesList();
            if (serviceInstancesList.size() > 0) {
                instanceId = serviceInstancesList.get(0).getValue();
                logger.debug("instance id: {}", instanceId);
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        while (instanceId == 0);

        return instanceId;
    }

    public static List<KeyStringValuePair> buildOSInfo() {
        List<KeyStringValuePair> osInfo = new ArrayList<KeyStringValuePair>();

        osInfo.add(KeyStringValuePair.newBuilder().setKey("os_name").setValue("osName").build());
        osInfo.add(KeyStringValuePair.newBuilder().setKey("host_name").setValue("hostName").build());
        osInfo.add(KeyStringValuePair.newBuilder().setKey("ipv4").setValue("ipv4").build());
        osInfo.add(KeyStringValuePair.newBuilder().setKey("process_no").setValue("123").build());
        osInfo.add(KeyStringValuePair.newBuilder().setKey("language").setValue("java").build());
        return osInfo;
    }
}
