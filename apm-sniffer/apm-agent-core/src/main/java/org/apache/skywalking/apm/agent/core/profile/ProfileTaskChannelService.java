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

package org.apache.skywalking.apm.agent.core.profile;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.commands.CommandService;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.*;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.apm.network.common.Commands;
import org.apache.skywalking.apm.network.language.agent.Downstream;
import org.apache.skywalking.apm.network.language.profile.ProfileTaskCommandQuery;
import org.apache.skywalking.apm.network.language.profile.ProfileTaskFinishReport;
import org.apache.skywalking.apm.network.language.profile.ProfileTaskGrpc;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

/**
 * Sniffer and backend, about the communication service of profile task protocol.
 * 1. Sniffer will check has new profile task list every {@link Config.Collector#GET_PROFILE_TASK_INTERVAL} second.
 * 2. When there is a new profile task snapshot, the data is transferred to the back end. use {@link org.apache.skywalking.apm.commons.datacarrier.DataCarrier} with
 * 3. When profiling task finish, it will send task finish status to backend
 *
 * @author MrPro
 */
@DefaultImplementor
public class ProfileTaskChannelService implements BootService, Runnable, GRPCChannelListener, IConsumer<ProfileTaskSegmentSnapshot> {
    private static final ILog logger = LogManager.getLogger(ProfileTaskChannelService.class);

    // channel status
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;

    // gRPC stub
    private volatile ProfileTaskGrpc.ProfileTaskBlockingStub profileTaskBlockingStub;
    private volatile ProfileTaskGrpc.ProfileTaskStub profileTaskStub;

    // query task list schedule
    private volatile ScheduledFuture<?> getTaskListFuture;

    // send snapshot to backend
    private volatile DataCarrier<ProfileTaskSegmentSnapshot> snapshotCarrier;
    private long snapshotUplinkedCounter;
    private long snapshotAbandonedCounter;
    private long snapshotLastLogTime;

    @Override
    public void run() {
        if (RemoteDownstreamConfig.Agent.SERVICE_ID != DictionaryUtil.nullValue()
                && RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID != DictionaryUtil.nullValue()
        ) {
            if (status == GRPCChannelStatus.CONNECTED) {
                try {
                    ProfileTaskCommandQuery.Builder builder = ProfileTaskCommandQuery.newBuilder();

                    // sniffer info
                    builder.setServiceId(RemoteDownstreamConfig.Agent.SERVICE_ID).setInstanceId(RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID);

                    // last command create time
                    builder.setLastCommandTime(ServiceManager.INSTANCE.findService(ProfileTaskExecutionService.class).getLastCommandCreateTime());

                    Commands commands = profileTaskBlockingStub.withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS).getProfileTaskCommands(builder.build());
                    ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
                } catch (Throwable t) {
                    if (!(t instanceof StatusRuntimeException)) {
                        logger.error(t, "Query profile task from backend fail.");
                        return;
                    }
                    final StatusRuntimeException statusRuntimeException = (StatusRuntimeException) t;
                    if (statusRuntimeException.getStatus().getCode() == Status.Code.UNIMPLEMENTED) {
                        logger.warn("Backend doesn't support profiling, profiling will be disabled");
                        if (getTaskListFuture != null) {
                            getTaskListFuture.cancel(true);
                        }
                    }
                }
            }
        }

    }

    @Override
    public void prepare() throws Throwable {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() throws Throwable {
        if (Config.Profile.ACTIVE) {
            snapshotLastLogTime = System.currentTimeMillis();

            // query task list
            getTaskListFuture = Executors.newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("ProfileGetTaskService"))
                    .scheduleWithFixedDelay(this, 0, Config.Collector.GET_PROFILE_TASK_INTERVAL, TimeUnit.SECONDS);

            // snapshot sender
            snapshotCarrier = new DataCarrier<>(Config.Profile.SNAPSHOT_SEND_CHANNEL_SIZE, Config.Profile.SNAPSHOT_SEND_BUFFER_SIZE);
            snapshotCarrier.setBufferStrategy(BufferStrategy.IF_POSSIBLE);
            snapshotCarrier.consume(this, 1);
        }
    }

    @Override
    public void onComplete() throws Throwable {
    }

    @Override
    public void shutdown() throws Throwable {
        if (getTaskListFuture != null) {
            getTaskListFuture.cancel(true);
        }

        if (snapshotCarrier != null) {
            snapshotCarrier.shutdownConsumers();
        }
    }

    @Override
    public void statusChanged(GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            profileTaskBlockingStub = ProfileTaskGrpc.newBlockingStub(channel);
            profileTaskStub = ProfileTaskGrpc.newStub(channel);
        } else {
            profileTaskBlockingStub = null;
            profileTaskStub = null;
        }
        this.status = status;
    }

    /**
     * add a new profiling snapshot, send to {@link #snapshotCarrier}
     * @param snapshot
     */
    public void addProfilingSnapshot(ProfileTaskSegmentSnapshot snapshot) {
        if (!snapshotCarrier.produce(snapshot)) {
            if (logger.isDebugEnable()) {
                logger.debug("One profile segment snapshot has been abandoned, cause by buffer is full.");
            }
        }
    }

    /**
     * notify backend profile task has finish
     * @param task
     */
    public void notifyProfileTaskFinish(ProfileTask task) {
        try {
            final ProfileTaskFinishReport.Builder reportBuilder = ProfileTaskFinishReport.newBuilder();
            // sniffer info
            reportBuilder.setServiceId(RemoteDownstreamConfig.Agent.SERVICE_ID).setInstanceId(RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID);
            // task info
            reportBuilder.setTaskId(task.getTaskId());

            // send data
            profileTaskBlockingStub.withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS).reportTaskFinish(reportBuilder.build());
        } catch (Throwable e) {
            logger.error(e, "Notify profile task finish to backend fail.");
        }
    }

    @Override
    public void init() {
    }

    @Override
    public void consume(List<ProfileTaskSegmentSnapshot> data) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            final GRPCStreamServiceStatus status = new GRPCStreamServiceStatus(false);
            final StreamObserver<org.apache.skywalking.apm.network.language.profile.ProfileTaskSegmentSnapshot> snapshotStreamObserver = profileTaskStub.withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS).collectSnapshot(new StreamObserver<Downstream>() {
                @Override
                public void onNext(Downstream downstream) {
                }

                @Override
                public void onError(Throwable throwable) {
                    status.finished();
                    if (logger.isErrorEnable()) {
                        logger.error(throwable, "Send profile segment snapshot to collector fail with a grpc internal exception.");
                    }
                    ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(throwable);
                }

                @Override
                public void onCompleted() {
                    status.finished();
                }
            });

            for (ProfileTaskSegmentSnapshot snapshot : data) {
                final org.apache.skywalking.apm.network.language.profile.ProfileTaskSegmentSnapshot transformSnapshot = snapshot.transform();
                snapshotStreamObserver.onNext(transformSnapshot);
            }

            snapshotStreamObserver.onCompleted();

            status.wait4Finish();
            snapshotUplinkedCounter += data.size();
        } else {
            snapshotAbandonedCounter += data.size();
        }

        printSnapshotUpLinkStatus();
    }

    @Override
    public void onError(List<ProfileTaskSegmentSnapshot> data, Throwable t) {
        logger.error(t, "Try to send {} profile segment snapshot to collector, with unexpected exception.", data.size());
    }

    @Override
    public void onExit() {

    }

    /**
     * print snapshot up link counter, same as {@link TraceSegmentServiceClient}
     */
    private void printSnapshotUpLinkStatus() {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - snapshotLastLogTime > 30 * 1000) {
            snapshotLastLogTime = currentTimeMillis;
            if (snapshotUplinkedCounter > 0) {
                logger.debug("{} profile segment snapshot have been sent to collector.", snapshotUplinkedCounter);
                snapshotUplinkedCounter = 0;
            }
            if (snapshotAbandonedCounter > 0) {
                logger.debug("{} profile segment snapshot have been abandoned, cause by no available channel.", snapshotAbandonedCounter);
                snapshotAbandonedCounter = 0;
            }
        }
    }
}
