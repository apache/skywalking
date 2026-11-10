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

package org.apache.skywalking.e2e;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.InetSocketAddress;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.skywalking.e2e.alarm.grpc.AlarmMessage;
import org.apache.skywalking.e2e.alarm.grpc.AlarmRecoveryMessage;
import org.apache.skywalking.e2e.alarm.grpc.AlarmServiceGrpc;
import org.apache.skywalking.e2e.alarm.grpc.Response;
import org.springframework.stereotype.Component;

/** In-process gRPC alarm hook for so11y e2e ({@value #PORT}). */
@Component
public class AlarmGrpcHookServer {
    public static final int PORT = 9898;

    private Server server;

    @PostConstruct
    public void start() throws IOException {
        server = NettyServerBuilder.forAddress(new InetSocketAddress("0.0.0.0", PORT))
                                   .addService(new AlarmServiceGrpc.AlarmServiceImplBase() {
                                       @Override
                                       public StreamObserver<AlarmMessage> doAlarm(
                                           final StreamObserver<Response> responseObserver) {
                                           return ack(responseObserver);
                                       }

                                       @Override
                                       public StreamObserver<AlarmRecoveryMessage> doAlarmRecovery(
                                           final StreamObserver<Response> responseObserver) {
                                           return ack(responseObserver);
                                       }

                                       private <T> StreamObserver<T> ack(final StreamObserver<Response> responseObserver) {
                                           return new StreamObserver<T>() {
                                               @Override
                                               public void onNext(final T value) {
                                               }

                                               @Override
                                               public void onError(final Throwable t) {
                                                   responseObserver.onError(t);
                                               }

                                               @Override
                                               public void onCompleted() {
                                                   responseObserver.onNext(Response.newBuilder().build());
                                                   responseObserver.onCompleted();
                                               }
                                           };
                                       }
                                   })
                                   .build()
                                   .start();
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdownNow();
        }
    }
}
