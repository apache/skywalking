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
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.network.language.agent.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
class RegisterMock {

    private static final Logger logger = LoggerFactory.getLogger(RegisterMock.class);

    private final ApplicationRegisterServiceGrpc.ApplicationRegisterServiceBlockingStub applicationRegisterServiceBlockingStub;
    private final InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceBlockingStub instanceDiscoveryServiceBlockingStub;

    RegisterMock(ManagedChannel channel) {
        applicationRegisterServiceBlockingStub = ApplicationRegisterServiceGrpc.newBlockingStub(channel);
        instanceDiscoveryServiceBlockingStub = InstanceDiscoveryServiceGrpc.newBlockingStub(channel);
    }

    int registerService(String serviceName) throws InterruptedException {
        Application.Builder application = Application.newBuilder();
        application.setApplicationCode(serviceName);

        ApplicationMapping applicationMapping;
        do {
            applicationMapping = applicationRegisterServiceBlockingStub.applicationCodeRegister(application.build());
            logger.debug("service id: {}", applicationMapping.getApplication().getValue());
            TimeUnit.MILLISECONDS.sleep(20);
        }
        while (applicationMapping.getApplication().getValue() == 0);

        return applicationMapping.getApplication().getValue();
    }

    int registerServiceInstance(int serviceId, String agentName) throws InterruptedException {
        ApplicationInstance.Builder instance = ApplicationInstance.newBuilder();
        instance.setApplicationId(serviceId);
        instance.setAgentUUID(agentName);
        instance.setRegisterTime(System.currentTimeMillis());

        OSInfo.Builder osInfo = OSInfo.newBuilder();
        osInfo.setHostname(agentName);
        osInfo.setOsName("MacOS XX");
        osInfo.setProcessNo(1001);
        osInfo.addIpv4S("10.0.0.3");
        osInfo.addIpv4S("10.0.0.4");
        instance.setOsinfo(osInfo);

        ApplicationInstanceMapping instanceMapping;
        do {
            instanceMapping = instanceDiscoveryServiceBlockingStub.registerInstance(instance.build());
            logger.debug("instance id: {}", instanceMapping.getApplicationInstanceId());
            TimeUnit.MILLISECONDS.sleep(20);
        }
        while (instanceMapping.getApplicationInstanceId() == 0);

        return instanceMapping.getApplicationInstanceId();
    }
}
