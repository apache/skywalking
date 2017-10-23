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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.skywalking.apm.network.proto.Application;
import org.skywalking.apm.network.proto.ApplicationMapping;
import org.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationRegisterServiceHandlerTestCase {

    private final Logger logger = LoggerFactory.getLogger(ApplicationRegisterServiceHandlerTestCase.class);

    private ApplicationRegisterServiceGrpc.ApplicationRegisterServiceBlockingStub stub;

    public void testRegister() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();
        stub = ApplicationRegisterServiceGrpc.newBlockingStub(channel);

        Application application = Application.newBuilder().addApplicationCode("test141").build();
        ApplicationMapping mapping = stub.register(application);
        logger.debug(mapping.getApplication(0).getKey() + ", " + mapping.getApplication(0).getValue());
    }
}
