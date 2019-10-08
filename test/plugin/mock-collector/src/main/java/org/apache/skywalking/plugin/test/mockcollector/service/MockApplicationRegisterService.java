/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.skywalking.plugin.test.mockcollector.service;

import io.grpc.stub.StreamObserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.network.language.agent.Application;
import org.apache.skywalking.apm.network.language.agent.ApplicationMapping;
import org.apache.skywalking.apm.network.language.agent.ApplicationRegisterServiceGrpc;
import org.apache.skywalking.apm.network.language.agent.KeyWithIntegerValue;
import org.apache.skywalking.plugin.test.mockcollector.entity.RegistryItem;
import org.apache.skywalking.plugin.test.mockcollector.entity.ValidateData;

/**
 * Created by xin on 2017/7/11.
 */
public class MockApplicationRegisterService extends ApplicationRegisterServiceGrpc.ApplicationRegisterServiceImplBase {
    private Logger logger = LogManager.getLogger(MockTraceSegmentService.class);

    @Override
    public void applicationCodeRegister(Application request, StreamObserver<ApplicationMapping> responseObserver) {
        logger.debug("receive application register.");
        String applicationCode = request.getApplicationCode();
        ApplicationMapping.Builder builder = ApplicationMapping.newBuilder();

        if (applicationCode.startsWith("localhost") || applicationCode.startsWith("127.0.0.1") || applicationCode.contains(":") || applicationCode.contains("/")) {
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
            return;
        }

        Integer applicationId = Sequences.SERVICE_MAPPING.get(applicationCode);
        if (applicationId == null) {
            applicationId = Sequences.ENDPOINT_SEQUENCE.incrementAndGet();
            Sequences.SERVICE_MAPPING.put(applicationCode, applicationId);
            ValidateData.INSTANCE.getRegistryItem().registryApplication(new RegistryItem.Application(applicationCode,
                    applicationId));
        }

        builder.setApplication(KeyWithIntegerValue.newBuilder().setKey(applicationCode).setValue(applicationId));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
