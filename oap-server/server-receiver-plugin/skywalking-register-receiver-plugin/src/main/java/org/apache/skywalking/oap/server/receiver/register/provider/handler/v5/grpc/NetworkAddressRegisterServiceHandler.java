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

package org.apache.skywalking.oap.server.receiver.register.provider.handler.v5.grpc;

import com.google.protobuf.ProtocolStringList;
import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.register.service.INetworkAddressInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressRegisterServiceHandler extends NetworkAddressRegisterServiceGrpc.NetworkAddressRegisterServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAddressRegisterServiceHandler.class);

    private final INetworkAddressInventoryRegister networkAddressInventoryRegister;

    public NetworkAddressRegisterServiceHandler(ModuleManager moduleManager) {
        this.networkAddressInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(INetworkAddressInventoryRegister.class);
    }

    @Override
    public void batchRegister(NetworkAddresses request, StreamObserver<NetworkAddressMappings> responseObserver) {
        if (logger.isDebugEnabled()) {
            logger.debug("register application");
        }

        ProtocolStringList addressesList = request.getAddressesList();

        NetworkAddressMappings.Builder builder = NetworkAddressMappings.newBuilder();
        for (String networkAddress : addressesList) {
            int addressId = networkAddressInventoryRegister.getOrCreate(networkAddress);

            if (addressId != Const.NONE) {
                KeyWithIntegerValue value = KeyWithIntegerValue.newBuilder().setKey(networkAddress).setValue(addressId).build();
                builder.addAddressIds(value);
            }
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
