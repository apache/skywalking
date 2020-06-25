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

package org.apache.skywalking.oap.server.receiver.meter.provider.handler;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterReportServiceGrpc;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.receiver.meter.provider.process.MeterProcessContext;
import org.apache.skywalking.oap.server.receiver.meter.provider.process.MeterProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Meter protocol receiver, collect and process the meters.
 */
public class MeterServiceHandler extends MeterReportServiceGrpc.MeterReportServiceImplBase implements GRPCHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MeterServiceHandler.class);

    private final MeterProcessContext processContext;

    public MeterServiceHandler(MeterProcessContext processContext) {
        this.processContext = processContext;
    }

    @Override
    public StreamObserver<MeterData> collect(StreamObserver<Commands> responseObserver) {
        final MeterProcessor processor = processContext.createProcessor();
        return new StreamObserver<MeterData>() {
            @Override
            public void onNext(MeterData meterData) {
                processor.read(meterData);
            }

            @Override
            public void onError(Throwable throwable) {
                processor.process();
                LOGGER.error(throwable.getMessage(), throwable);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                processor.process();
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
