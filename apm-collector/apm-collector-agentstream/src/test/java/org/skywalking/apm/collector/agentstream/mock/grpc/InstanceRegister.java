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

package org.skywalking.apm.collector.agentstream.mock.grpc;

import io.grpc.ManagedChannel;
import org.skywalking.apm.network.proto.ApplicationInstance;
import org.skywalking.apm.network.proto.ApplicationInstanceMapping;
import org.skywalking.apm.network.proto.InstanceDiscoveryServiceGrpc;
import org.skywalking.apm.network.proto.OSInfo;

/**
 * @author peng-yongsheng
 */
public class InstanceRegister {

    public static int register(ManagedChannel channel, String agentUUId, Integer applicationId,
        String hostName, int processNo) {
        InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceBlockingStub stub = InstanceDiscoveryServiceGrpc.newBlockingStub(channel);
        ApplicationInstance.Builder instance = ApplicationInstance.newBuilder();
        instance.setApplicationId(applicationId);
        instance.setRegisterTime(System.currentTimeMillis());
        instance.setAgentUUID(agentUUId);

        OSInfo.Builder osInfo = OSInfo.newBuilder();
        osInfo.setHostname(hostName);
        osInfo.setOsName("Linux");
        osInfo.setProcessNo(processNo);
        osInfo.addIpv4S("10.0.0.1");
        osInfo.addIpv4S("10.0.0.2");
        instance.setOsinfo(osInfo.build());

        ApplicationInstanceMapping mapping = stub.register(instance.build());
        int instanceId = mapping.getApplicationInstanceId();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }
        return instanceId;
    }
}
