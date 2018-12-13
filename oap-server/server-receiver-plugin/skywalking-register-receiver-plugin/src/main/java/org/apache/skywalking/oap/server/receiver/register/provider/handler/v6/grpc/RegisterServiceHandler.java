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

package org.apache.skywalking.oap.server.receiver.register.provider.handler.v6.grpc;

import com.google.common.base.Strings;
import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.common.*;
import org.apache.skywalking.apm.network.register.v2.*;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.register.service.*;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.receiver.register.provider.handler.v5.grpc.InstanceDiscoveryServiceHandler;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class RegisterServiceHandler extends RegisterGrpc.RegisterImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(InstanceDiscoveryServiceHandler.class);

    private final ServiceInventoryCache serviceInventoryCache;
    private final ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    private final IServiceInventoryRegister serviceInventoryRegister;
    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
    private final IEndpointInventoryRegister inventoryService;
    private final INetworkAddressInventoryRegister networkAddressInventoryRegister;

    public RegisterServiceHandler(ModuleManager moduleManager) {
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        this.serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class);
        this.serviceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInventoryRegister.class);
        this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInstanceInventoryRegister.class);
        this.inventoryService = moduleManager.find(CoreModule.NAME).provider().getService(IEndpointInventoryRegister.class);
        this.networkAddressInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(INetworkAddressInventoryRegister.class);
    }

    @Override public void doServiceRegister(Services request, StreamObserver<ServiceRegisterMapping> responseObserver) {
        ServiceRegisterMapping.Builder builder = ServiceRegisterMapping.newBuilder();
        request.getServicesList().forEach(service -> {
            String serviceName = service.getServiceName();
            if (logger.isDebugEnabled()) {
                logger.debug("Register service, service code: {}", serviceName);
            }
            int serviceId = serviceInventoryRegister.getOrCreate(serviceName);

            if (serviceId != Const.NONE) {
                KeyIntValuePair value = KeyIntValuePair.newBuilder().setKey(serviceName).setValue(serviceId).build();
                builder.addServices(value);
            }
        });

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override public void doServiceInstanceRegister(ServiceInstances request,
        StreamObserver<ServiceInstanceRegisterMapping> responseObserver) {

        ServiceInstanceRegisterMapping.Builder builder = ServiceInstanceRegisterMapping.newBuilder();

        request.getInstancesList().forEach(instance -> {
            ServiceInventory serviceInventory = serviceInventoryCache.get(instance.getServiceId());

            ServiceInstanceInventory.AgentOsInfo agentOsInfo = new ServiceInstanceInventory.AgentOsInfo();
            for (KeyStringValuePair property : instance.getPropertiesList()) {
                String key = property.getKey();
                switch (key) {
                    case "OSName":
                        agentOsInfo.setOsName(property.getValue());
                        break;
                    case "hostname":
                        agentOsInfo.setHostname(property.getValue());
                        break;
                    case "ipv4":
                        agentOsInfo.getIpv4s().add(property.getValue());
                        break;
                    case "ProcessNo":
                        agentOsInfo.setProcessNo(Integer.parseInt(property.getValue()));
                        break;
                }
            }

            String instanceName = serviceInventory.getName();
            if (agentOsInfo.getProcessNo() != 0) {
                instanceName += "-pid:" + agentOsInfo.getProcessNo();
            }
            if (!Strings.isNullOrEmpty(agentOsInfo.getHostname())) {
                instanceName += "@" + agentOsInfo.getHostname();
            }

            int serviceInstanceId = serviceInstanceInventoryRegister.getOrCreate(instance.getServiceId(), instanceName, instance.getInstanceUUID(), instance.getTime(), agentOsInfo);

            if (serviceInstanceId != Const.NONE) {
                logger.info("register service instance id={} [UUID:{}]", serviceInstanceId, instance.getInstanceUUID());
                builder.addServiceInstances(KeyIntValuePair.newBuilder().setKey(instance.getInstanceUUID()).setValue(serviceInstanceId));
            }
        });

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override public void doEndpointRegister(Enpoints request, StreamObserver<EndpointMapping> responseObserver) {
        EndpointMapping.Builder builder = EndpointMapping.newBuilder();

        request.getEndpointsList().forEach(endpoint -> {
            int serviceId = endpoint.getServiceId();
            String endpointName = endpoint.getEndpointName();

            int endpointId = inventoryService.getOrCreate(serviceId, endpointName, DetectPoint.fromNetworkProtocolDetectPoint(endpoint.getFrom()));

            if (endpointId != Const.NONE) {
                builder.addElements(EndpointMappingElement.newBuilder()
                    .setServiceId(serviceId)
                    .setEndpointName(endpointName)
                    .setEndpointId(endpointId)
                    .setFrom(endpoint.getFrom()));
            }
        });

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void doNetworkAddressRegister(NetAddresses request, StreamObserver<NetAddressMapping> responseObserver) {
        NetAddressMapping.Builder builder = NetAddressMapping.newBuilder();

        request.getAddressesList().forEach(networkAddress -> {
            int addressId = networkAddressInventoryRegister.getOrCreate(networkAddress);

            if (addressId != Const.NONE) {
                builder.addAddressIds(KeyIntValuePair.newBuilder().setKey(networkAddress).setValue(addressId));
            }
        });

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override public void doServiceAndNetworkAddressMappingRegister(ServiceAndNetworkAddressMappings request,
        StreamObserver<Commands> responseObserver) {

        request.getMappingsList().forEach(mapping -> {
            int serviceId = mapping.getServiceId();

            if (serviceId == Const.NONE) {
                int serviceInstanceId = mapping.getServiceInstanceId();
                if (serviceInstanceId == Const.NONE) {
                    serviceId = serviceInstanceInventoryCache.get(serviceInstanceId).getServiceId();
                } else {
                    return;
                }
            }

            if (serviceId == Const.NONE) {
                return;
            }

            int networkAddressId = mapping.getNetworkAddressId();
            if (networkAddressId == Const.NONE) {
                String address = mapping.getNetworkAddress();
                if (StringUtil.isEmpty(address)) {
                    return;
                }

                networkAddressId = networkAddressInventoryRegister.getOrCreate(address);
                if (networkAddressId == Const.NONE) {
                    return;
                }
            }

            serviceInventoryRegister.updateMapping(networkAddressId, serviceId);
        });

        responseObserver.onNext(Commands.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
