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

package org.apache.skywalking.apm.testcase.grpc.controller;

import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.testcase.grpc.consumr.ConsumerInterceptor;
import org.apache.skywalking.apm.testcase.grpc.proto.GreeterBlockingErrorGrpc;
import org.apache.skywalking.apm.testcase.grpc.proto.GreeterBlockingGrpc;
import org.apache.skywalking.apm.testcase.grpc.proto.GreeterGrpc;
import org.apache.skywalking.apm.testcase.grpc.proto.HelloReply;
import org.apache.skywalking.apm.testcase.grpc.proto.HelloRequest;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case")
public class CaseController {

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    private static final String SUCCESS = "Success";

    private final String grpcProviderHost = "127.0.0.1";
    private final int grpcProviderPort = 18080;
    private ManagedChannel channel;
    private GreeterGrpc.GreeterStub greeterStub;
    private GreeterBlockingGrpc.GreeterBlockingBlockingStub greeterBlockingStub;
    private GreeterBlockingErrorGrpc.GreeterBlockingErrorBlockingStub greeterBlockingErrorStub;

    @PostConstruct
    public void up() {
        channel = ManagedChannelBuilder.forAddress(grpcProviderHost, grpcProviderPort).usePlaintext(true).build();
        greeterStub = GreeterGrpc.newStub(ClientInterceptors.intercept(channel, new ConsumerInterceptor()));
        greeterBlockingStub = GreeterBlockingGrpc.newBlockingStub(ClientInterceptors.intercept(channel, new ConsumerInterceptor()));
        greeterBlockingErrorStub = GreeterBlockingErrorGrpc.newBlockingStub(ClientInterceptors.intercept(channel, new ConsumerInterceptor()));
    }

    @RequestMapping("/correlation-autotag-scenario")
    @ResponseBody
    public String testcase() {
        TraceContext.putCorrelation("autotag1", "1");
        TraceContext.putCorrelation("autotag2", "1");
        greetService();
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        // your codes
        return SUCCESS;
    }

    private static List<String> names() {
        return Arrays.asList("Sophia", "Jackson");
    }

    private void greetService() {
        ClientResponseObserver<HelloRequest, HelloReply> helloReplyStreamObserver = new ClientResponseObserver<HelloRequest, HelloReply>() {
            private ClientCallStreamObserver<HelloRequest> requestStream;

            @Override
            public void beforeStart(ClientCallStreamObserver observer) {
                this.requestStream = observer;
                this.requestStream.setOnReadyHandler(new Runnable() {
                    Iterator<String> iterator = names().iterator();

                    @Override
                    public void run() {
                        while (requestStream.isReady()) {
                            if (iterator.hasNext()) {
                                String name = iterator.next();
                                HelloRequest request = HelloRequest.newBuilder().setName(name).build();
                                requestStream.onNext(request);
                            } else {
                                requestStream.onCompleted();
                            }
                        }
                    }
                });
            }

            @Override
            public void onNext(HelloReply reply) {
                LOGGER.info("Receive an message from provider. message: {}", reply.getMessage());
                requestStream.request(1);
            }

            public void onError(Throwable throwable) {
                LOGGER.error("Failed to send data", throwable);
            }

            public void onCompleted() {
                LOGGER.info("All Done");
            }
        };

        greeterStub.sayHello(helloReplyStreamObserver);
    }
}
