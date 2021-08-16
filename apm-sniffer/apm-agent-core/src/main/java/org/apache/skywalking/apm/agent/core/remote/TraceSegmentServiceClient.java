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

package org.apache.skywalking.apm.agent.core.remote;

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.commands.CommandService;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.context.TracingContextListener;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.TraceSegmentReportServiceGrpc;

import static org.apache.skywalking.apm.agent.core.conf.Config.Buffer.BUFFER_SIZE;
import static org.apache.skywalking.apm.agent.core.conf.Config.Buffer.CHANNEL_SIZE;
import static org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus.CONNECTED;

@DefaultImplementor
public class TraceSegmentServiceClient implements BootService, IConsumer<TraceSegment>, TracingContextListener, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(TraceSegmentServiceClient.class);

    private long lastLogTime;
    private long segmentUplinkedCounter;
    private long segmentAbandonedCounter;
    private volatile DataCarrier<TraceSegment> carrier;
    private volatile TraceSegmentReportServiceGrpc.TraceSegmentReportServiceStub serviceStub;
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;

    @Override
    public void prepare() {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() {
        lastLogTime = System.currentTimeMillis();
        segmentUplinkedCounter = 0;
        segmentAbandonedCounter = 0;
        carrier = new DataCarrier<>(CHANNEL_SIZE, BUFFER_SIZE, BufferStrategy.IF_POSSIBLE);
        carrier.consume(this, 1);
    }

    @Override
    public void onComplete() {
        TracingContext.ListenerManager.add(this);
    }

    @Override
    public void shutdown() {
        TracingContext.ListenerManager.remove(this);
        carrier.shutdownConsumers();
    }

    @Override
    public void init(final Properties properties) {

    }

    @Override
    public void consume(List<TraceSegment> data) {
        if (CONNECTED.equals(status)) {
            final GRPCStreamServiceStatus status = new GRPCStreamServiceStatus(false);
            StreamObserver<SegmentObject> upstreamSegmentStreamObserver = serviceStub.withDeadlineAfter(
                Config.Collector.GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS
            ).collect(new StreamObserver<Commands>() {
                @Override
                public void onNext(Commands commands) {
                    ServiceManager.INSTANCE.findService(CommandService.class)
                                           .receiveCommand(commands);
                }

                @Override
                public void onError(
                    Throwable throwable) {
                    status.finished();
                    if (LOGGER.isErrorEnable()) {
                        LOGGER.error(
                            throwable,
                            "Send UpstreamSegment to collector fail with a grpc internal exception."
                        );
                    }
                    ServiceManager.INSTANCE
                        .findService(GRPCChannelManager.class)
                        .reportError(throwable);
                }

                @Override
                public void onCompleted() {
                    status.finished();
                }
            });

            try {
                for (TraceSegment segment : data) {
                    SegmentObject upstreamSegment = segment.transform();
                    upstreamSegmentStreamObserver.onNext(upstreamSegment);
                }
            } catch (Throwable t) {
                LOGGER.error(t, "Transform and send UpstreamSegment to collector fail.");
            }

            upstreamSegmentStreamObserver.onCompleted();

            status.wait4Finish();
            segmentUplinkedCounter += data.size();
        } else {
            segmentAbandonedCounter += data.size();
        }

        printUplinkStatus();
    }

    private void printUplinkStatus() {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastLogTime > 30 * 1000) {
            lastLogTime = currentTimeMillis;
            if (segmentUplinkedCounter > 0) {
                LOGGER.debug("{} trace segments have been sent to collector.", segmentUplinkedCounter);
                segmentUplinkedCounter = 0;
            }
            if (segmentAbandonedCounter > 0) {
                LOGGER.debug(
                    "{} trace segments have been abandoned, cause by no available channel.", segmentAbandonedCounter);
                segmentAbandonedCounter = 0;
            }
        }
    }

    @Override
    public void onError(List<TraceSegment> data, Throwable t) {
        LOGGER.error(t, "Try to send {} trace segments to collector, with unexpected exception.", data.size());
    }

    @Override
    public void onExit() {

    }

    @Override
    public void afterFinished(TraceSegment traceSegment) {
        if (traceSegment.isIgnore()) {
            return;
        }
        if (!carrier.produce(traceSegment)) {
            if (LOGGER.isDebugEnable()) {
                LOGGER.debug("One trace segment has been abandoned, cause by buffer is full.");
            }
        }
    }

    @Override
    public void statusChanged(GRPCChannelStatus status) {
        if (CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            serviceStub = TraceSegmentReportServiceGrpc.newStub(channel);
        }
        this.status = status;
    }
}
