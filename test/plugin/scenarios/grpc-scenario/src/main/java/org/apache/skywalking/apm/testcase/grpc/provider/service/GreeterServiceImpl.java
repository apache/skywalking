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

package org.apache.skywalking.apm.testcase.grpc.provider.service;

import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.testcase.grpc.proto.GreeterGrpc;
import org.apache.skywalking.apm.testcase.grpc.proto.HelloReply;
import org.apache.skywalking.apm.testcase.grpc.proto.HelloRequest;

public class GreeterServiceImpl extends GreeterGrpc.GreeterImplBase {

    private static final Logger LOGGER = LogManager.getLogger(GreeterServiceImpl.class);

    @Override
    public StreamObserver<HelloRequest> sayHello(final StreamObserver<HelloReply> responseObserver) {
        StreamObserver<HelloRequest> requestStreamObserver = new StreamObserver<HelloRequest>() {

            public void onNext(HelloRequest request) {
                LOGGER.info("Receive an message from client. Message: {}", request.getName());
                responseObserver.onNext(HelloReply.newBuilder().setMessage("Hi," + request.getName()).build());
            }

            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            public void onCompleted() {
                LOGGER.info("End the stream.");
                responseObserver.onCompleted();
            }
        };
        return requestStreamObserver;
    }
}
