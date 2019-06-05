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

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.register.service.IServiceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ApplicationRegisterHandler extends ApplicationRegisterServiceGrpc.ApplicationRegisterServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationRegisterHandler.class);

    private final IServiceInventoryRegister serviceInventoryRegister;

    public ApplicationRegisterHandler(ModuleManager moduleManager) {
        serviceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInventoryRegister.class);
    }

    @Override
    public void applicationCodeRegister(Application request, StreamObserver<ApplicationMapping> responseObserver) {
        if (logger.isDebugEnabled()) {
            logger.debug("Register application, application code: {}", request.getApplicationCode());
        }

        ApplicationMapping.Builder builder = ApplicationMapping.newBuilder();
        String serviceName = request.getApplicationCode();
        int serviceId = serviceInventoryRegister.getOrCreate(serviceName, null);

        if (serviceId != Const.NONE) {
            KeyWithIntegerValue value = KeyWithIntegerValue.newBuilder().setKey(serviceName).setValue(serviceId).build();
            builder.setApplication(value);
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
