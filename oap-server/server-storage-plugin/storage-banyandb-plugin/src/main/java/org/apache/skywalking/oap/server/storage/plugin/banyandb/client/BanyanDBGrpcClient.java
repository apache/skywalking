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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.skywalking.banyandb.Query;
import org.apache.skywalking.banyandb.TraceServiceGrpc;
import org.apache.skywalking.banyandb.Write;

import java.util.List;

public class BanyanDBGrpcClient {
    private final TraceServiceGrpc.TraceServiceBlockingStub blockingStub;
    private final TraceServiceGrpc.TraceServiceStub asyncStub;

    public BanyanDBGrpcClient(String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).build();
        blockingStub = TraceServiceGrpc.newBlockingStub(channel);
        asyncStub = TraceServiceGrpc.newStub(channel);
    }

    public Query.QueryResponse query(Query.QueryRequest queryRequest) {
        return this.blockingStub.query(queryRequest);
    }

    public void write(Write.WriteRequest req) {
        StreamObserver<Write.WriteRequest> requestObserver =
                asyncStub.write(new StreamObserver<Write.WriteResponse>() {
                    @Override
                    public void onNext(Write.WriteResponse writeResponse) {

                    }

                    @Override
                    public void onError(Throwable throwable) {

                    }

                    @Override
                    public void onCompleted() {

                    }
                });
        requestObserver.onNext(req);
    }
}
