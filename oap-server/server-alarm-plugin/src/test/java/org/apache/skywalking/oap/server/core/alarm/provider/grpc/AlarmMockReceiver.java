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

package org.apache.skywalking.oap.server.core.alarm.provider.grpc;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.grpc.AlarmServiceGrpc;
import org.apache.skywalking.oap.server.core.alarm.grpc.Response;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;

@Slf4j
public class AlarmMockReceiver {
    public static void main(String[] args) throws ServerException, InterruptedException {
        GRPCServer server = new GRPCServer("localhost", 9888);
        server.initialize();
        server.addHandler(new MockAlarmHandler());
        server.start();

        while (true) {
            Thread.sleep(20000L);
        }
    }

    public static class MockAlarmHandler extends AlarmServiceGrpc.AlarmServiceImplBase implements GRPCHandler {

        @Override public StreamObserver<AlarmMessage> doAlarm(StreamObserver<Response> responseObserver) {
            return new StreamObserver<AlarmMessage>() {
                @Override
                public void onNext(AlarmMessage value) {
                    log.info("received alarm message: {}", value.toString());
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                    if (log.isDebugEnabled()) {
                        log.debug("received alarm message error.");
                    }
                    responseObserver.onCompleted();
                }

                @Override
                public void onCompleted() {
                    responseObserver.onNext(Response.newBuilder().build());
                    responseObserver.onCompleted();
                    if (log.isDebugEnabled()) {
                        log.debug("received alarm message completed.");
                    }
                }
            };
        }
    }
}