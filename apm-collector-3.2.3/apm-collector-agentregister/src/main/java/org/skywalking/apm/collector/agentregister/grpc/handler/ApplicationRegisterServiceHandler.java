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

package org.skywalking.apm.collector.agentregister.grpc.handler;

import com.google.protobuf.ProtocolStringList;
import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.agentregister.application.ApplicationIDService;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.Application;
import org.skywalking.apm.network.proto.ApplicationMapping;
import org.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;
import org.skywalking.apm.network.proto.KeyWithIntegerValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationRegisterServiceHandler extends ApplicationRegisterServiceGrpc.ApplicationRegisterServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(ApplicationRegisterServiceHandler.class);

    private ApplicationIDService applicationIDService = new ApplicationIDService();

    @Override public void register(Application request, StreamObserver<ApplicationMapping> responseObserver) {
        logger.debug("register application");
        ProtocolStringList applicationCodes = request.getApplicationCodeList();

        ApplicationMapping.Builder builder = ApplicationMapping.newBuilder();
        for (int i = 0; i < applicationCodes.size(); i++) {
            String applicationCode = applicationCodes.get(i);
            int applicationId = applicationIDService.getOrCreate(applicationCode);

            if (applicationId != 0) {
                KeyWithIntegerValue value = KeyWithIntegerValue.newBuilder().setKey(applicationCode).setValue(applicationId).build();
                builder.addApplication(value);
            }
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
