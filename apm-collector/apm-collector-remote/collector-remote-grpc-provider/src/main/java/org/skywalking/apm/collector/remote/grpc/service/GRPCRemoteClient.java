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

package org.skywalking.apm.collector.remote.grpc.service;

import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteMessage;
import org.skywalking.apm.collector.remote.service.RemoteClient;

/**
 * @author peng-yongsheng
 */
public class GRPCRemoteClient implements RemoteClient {

    private final GRPCRemoteSerializeService service;
    private final StreamObserver<RemoteMessage> streamObserver;
    private final String address;

    public GRPCRemoteClient(String host, int port, StreamObserver<RemoteMessage> streamObserver) {
        this.address = host + ":" + String.valueOf(port);
        this.streamObserver = streamObserver;
        this.service = new GRPCRemoteSerializeService();
    }

    @Override public final String getAddress() {
        return this.address;
    }

    @Override public void send(int graphId, int nodeId, Data data) {
        RemoteMessage.Builder builder = RemoteMessage.newBuilder();
        builder.setGraphId(graphId);
        builder.setNodeId(nodeId);
        builder.setRemoteData(service.serialize(data));

        streamObserver.onNext(builder.build());
    }

    @Override public boolean equals(String address) {
        return this.address.equals(address);
    }

    @Override public int compareTo(RemoteClient o) {
        return this.address.compareTo(o.getAddress());
    }
}
