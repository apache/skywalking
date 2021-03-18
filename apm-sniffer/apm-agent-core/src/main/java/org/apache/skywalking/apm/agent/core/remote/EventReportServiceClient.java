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
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.commands.CommandService;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.event.v3.Event;
import org.apache.skywalking.apm.network.event.v3.EventServiceGrpc;
import org.apache.skywalking.apm.network.event.v3.Source;
import org.apache.skywalking.apm.network.event.v3.Type;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;
import static org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus.CONNECTED;

@DefaultImplementor
public class EventReportServiceClient implements BootService, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(EventReportServiceClient.class);

    private final AtomicBoolean reported = new AtomicBoolean();

    private Event.Builder startingEvent;

    private EventServiceGrpc.EventServiceStub eventServiceStub;

    private GRPCChannelStatus status;

    @Override
    public void prepare() throws Throwable {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);

        final RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        startingEvent = Event.newBuilder()
                             .setUuid(UUID.randomUUID().toString())
                             .setName("Start")
                             .setStartTime(runtimeMxBean.getStartTime())
                             .setMessage("Start Java Application")
                             .setType(Type.Normal)
                             .setSource(
                                 Source.newBuilder()
                                       .setService(Config.Agent.SERVICE_NAME)
                                       .setServiceInstance(Config.Agent.INSTANCE_NAME)
                                       .build()
                             )
                             .putParameters(
                                 "OPTS",
                                 runtimeMxBean.getInputArguments()
                                              .stream()
                                              .sorted()
                                              .collect(Collectors.joining(" "))
                             );
    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void onComplete() throws Throwable {
        startingEvent.setEndTime(System.currentTimeMillis());

        reportStartingEvent();
    }

    @Override
    public void shutdown() throws Throwable {
        if (!CONNECTED.equals(status)) {
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final Event.Builder shutdownEvent = Event.newBuilder()
                                                 .setUuid(UUID.randomUUID().toString())
                                                 .setName("Shutdown")
                                                 .setStartTime(System.currentTimeMillis())
                                                 .setEndTime(System.currentTimeMillis())
                                                 .setMessage("Shutting down Java Application")
                                                 .setType(Type.Normal)
                                                 .setSource(
                                                     Source.newBuilder()
                                                           .setService(Config.Agent.SERVICE_NAME)
                                                           .setServiceInstance(Config.Agent.INSTANCE_NAME)
                                                           .build()
                                                 );

        final StreamObserver<Event> collector = eventServiceStub.collect(new StreamObserver<Commands>() {
            @Override
            public void onNext(final Commands commands) {
                ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
            }

            @Override
            public void onError(final Throwable t) {
                LOGGER.error("Failed to report shutdown event.", t);
                // Ignore status change at shutting down stage.
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        collector.onNext(shutdownEvent.build());
        collector.onCompleted();
        latch.await();
    }

    @Override
    public void statusChanged(final GRPCChannelStatus status) {
        this.status = status;

        if (!CONNECTED.equals(status)) {
            return;
        }

        final Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
        eventServiceStub = EventServiceGrpc.newStub(channel);
        eventServiceStub = eventServiceStub.withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS);

        reportStartingEvent();
    }

    private void reportStartingEvent() {
        if (reported.compareAndSet(false, true)) {
            return;
        }

        final StreamObserver<Event> collector = eventServiceStub.collect(new StreamObserver<Commands>() {
            @Override
            public void onNext(final Commands commands) {
                ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
            }

            @Override
            public void onError(final Throwable t) {
                LOGGER.error("Failed to report starting event.", t);
                ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(t);
                reported.set(false);
            }

            @Override
            public void onCompleted() {
            }
        });

        collector.onNext(startingEvent.build());
        collector.onCompleted();
    }
}
