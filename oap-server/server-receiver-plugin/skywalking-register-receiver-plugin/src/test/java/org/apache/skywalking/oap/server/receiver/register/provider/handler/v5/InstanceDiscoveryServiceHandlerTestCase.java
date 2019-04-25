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

package org.apache.skywalking.oap.server.receiver.register.provider.handler.v5;

import io.grpc.*;
import org.apache.skywalking.apm.network.language.agent.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class InstanceDiscoveryServiceHandlerTestCase {

    private static final Logger logger = LoggerFactory.getLogger(InstanceDiscoveryServiceHandlerTestCase.class);

    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();

        InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceBlockingStub stub = InstanceDiscoveryServiceGrpc.newBlockingStub(channel);

        ApplicationInstance.Builder applicationInstance = ApplicationInstance.newBuilder();
        applicationInstance.setApplicationId(1);
        applicationInstance.setAgentUUID("Test");
        applicationInstance.setRegisterTime(System.currentTimeMillis());

        OSInfo.Builder osInfo = OSInfo.newBuilder();
        osInfo.setOsName("mac os");
        osInfo.setHostname("pengys");
        osInfo.setProcessNo(1);
        osInfo.addIpv4S("10.0.0.1");
        osInfo.addIpv4S("10.0.0.2");
        applicationInstance.setOsinfo(osInfo);

        ApplicationInstanceMapping instanceMapping = stub.registerInstance(applicationInstance.build());
        logger.info("application id: {}, application instance id: {}", instanceMapping.getApplicationId(), instanceMapping.getApplicationInstanceId());
    }
}
