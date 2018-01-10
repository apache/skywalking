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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.skywalking.apm.network.proto.ApplicationInstanceHeartbeat;
import org.apache.skywalking.apm.network.proto.InstanceDiscoveryServiceGrpc;

/**
 * @author peng-yongsheng
 */
public class InstHeartBeatServiceTestCase {

    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();
        InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceBlockingStub blockingStub = InstanceDiscoveryServiceGrpc.newBlockingStub(channel);

        ApplicationInstanceHeartbeat.Builder builder = ApplicationInstanceHeartbeat.newBuilder();
        builder.setApplicationInstanceId(2);
        builder.setHeartbeatTime(System.currentTimeMillis());
        blockingStub.heartbeat(builder.build());
    }
}
