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

package org.apache.skywalking.apm.collector.agent.grpc.provider.handler;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.collector.analysis.register.define.AnalysisRegisterModule;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IApplicationIDService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.apache.skywalking.apm.network.proto.Application;
import org.apache.skywalking.apm.network.proto.ApplicationMapping;
import org.apache.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;
import org.apache.skywalking.apm.network.proto.KeyWithIntegerValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationRegisterServiceHandler extends ApplicationRegisterServiceGrpc.ApplicationRegisterServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationRegisterServiceHandler.class);

    private final IApplicationIDService applicationIDService;

    public ApplicationRegisterServiceHandler(ModuleManager moduleManager) {
        applicationIDService = moduleManager.find(AnalysisRegisterModule.NAME).getService(IApplicationIDService.class);
    }

    @Override
    public void applicationCodeRegister(Application request, StreamObserver<ApplicationMapping> responseObserver) {
        if (logger.isDebugEnabled()) {
            logger.debug("register application");
        }

        ApplicationMapping.Builder builder = ApplicationMapping.newBuilder();
        String applicationCode = request.getApplicationCode();
        int applicationId = applicationIDService.getOrCreateForApplicationCode(applicationCode);

        if (applicationId != 0) {
            KeyWithIntegerValue value = KeyWithIntegerValue.newBuilder().setKey(applicationCode).setValue(applicationId).build();
            builder.setApplication(value);
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
