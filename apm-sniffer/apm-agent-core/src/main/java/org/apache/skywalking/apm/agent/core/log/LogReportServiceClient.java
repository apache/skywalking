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

package org.apache.skywalking.apm.agent.core.log;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCStreamServiceStatus;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogReportServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

@DefaultImplementor
public class LogReportServiceClient implements BootService, IConsumer<LogData> {

    private static final ILog LOGGER = LogManager.getLogger(LogReportServiceClient.class);

    private volatile DataCarrier<LogData> carrier;

    private LogReportServiceGrpc.LogReportServiceStub asyncStub;

    private ManagedChannel channel;

    private AtomicBoolean disconnected = new AtomicBoolean(false);

    @Override
    public void prepare() throws Throwable {

    }

    @Override
    public void boot() throws Throwable {
        carrier = new DataCarrier<>("gRPC-log", "gRPC-log", Config.Buffer.CHANNEL_SIZE, Config.Buffer.BUFFER_SIZE,
                BufferStrategy.IF_POSSIBLE);
        carrier.consume(this, 1);
        channel = ManagedChannelBuilder.forAddress(Config.GRPCLog.SERVER_HOST, Config.GRPCLog.SERVER_PORT)
                .usePlaintext().build();
        asyncStub = LogReportServiceGrpc.newStub(channel)
                .withMaxOutboundMessageSize(Config.GRPCLog.MAX_MESSAGE_SIZE);
    }

    @Override
    public void onComplete() throws Throwable {
    }

    public void shutdown() {
        try {
            if (channel != null) {
                channel.shutdownNow();
            }
            carrier.shutdownConsumers();
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    @Override
    public void init() {

    }

    public void produce(LogData logData) {
        if (Objects.nonNull(logData)) {
            if (!carrier.produce(logData)) {
                LOGGER.error("LogReportServiceClient discard logData: " + logData.toString());
            }
        }
    }

    @Override
    public void consume(final List<LogData> dataList) {
        if (CollectionUtil.isEmpty(dataList)) {
            return;
        }
        StreamObserver<LogData> reportStreamObserver = null;
        final GRPCStreamServiceStatus waitStatus = new GRPCStreamServiceStatus(false);
        try {
            reportStreamObserver = asyncStub.withDeadlineAfter(
                    Config.GRPCLog.UPSTREAM_TIMEOUT, TimeUnit.SECONDS
            ).collect(new StreamObserver<Commands>() {
                @Override
                public void onNext(Commands commands) {
                }

                @Override
                public void onError(Throwable t) {
                    waitStatus.finished();
                    if (!disconnected.get()) {
                        LOGGER.error("Send log to gRPC server fail with an internal exception.", t);
                        disconnected.set(true);
                    }

                    LOGGER.error("LogReportServiceClient discard logData: " + dataList.toString());
                }

                @Override
                public void onCompleted() {
                    if (disconnected.get()) {
                        disconnected.set(false);
                    }
                    waitStatus.finished();
                }
            });

            for (final LogData logData : dataList) {
                reportStreamObserver.onNext(logData);
            }
        } catch (Throwable e) {
            if (!(e instanceof StatusRuntimeException)) {
                LOGGER.error(e, "Report grpc log to server fail.");
            }
        } finally {
            if (reportStreamObserver != null) {
                reportStreamObserver.onCompleted();
            }
            waitStatus.wait4Finish();
        }
    }

    @Override
    public void onError(List<LogData> data, Throwable t) {

    }

    @Override
    public void onExit() {

    }
}
