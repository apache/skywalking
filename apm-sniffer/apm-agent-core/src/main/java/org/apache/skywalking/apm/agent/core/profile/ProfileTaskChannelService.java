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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.commands.CommandService;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskCommandQuery;
import org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskFinishReport;
import org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskGrpc;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

/**
 * Sniffer and backend, about the communication service of profile task protocol. 1. Sniffer will check has new profile
 * task list every {@link Config.Collector#GET_PROFILE_TASK_INTERVAL} second. 2. When there is a new profile task
 * snapshot, the data is transferred to the back end. use {@link LinkedBlockingQueue} 3. When profiling task finish, it
 * will send task finish status to backend
 */
@DefaultImplementor
public class ProfileTaskChannelService implements BootService, Runnable, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(ProfileTaskChannelService.class);

    // channel status
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;

    // gRPC stub
    private volatile ProfileTaskGrpc.ProfileTaskBlockingStub profileTaskBlockingStub;


    // segment snapshot sender
    private final BlockingQueue<TracingThreadSnapshot> snapshotQueue = new LinkedBlockingQueue<>(
        Config.Profile.SNAPSHOT_TRANSPORT_BUFFER_SIZE);
    private volatile ScheduledFuture<?> sendSnapshotFuture;

    // query task list schedule
    private volatile ScheduledFuture<?> getTaskListFuture;

    private ProfileSnapshotSender sender;

    @Override
    public void run() {
        if (status == GRPCChannelStatus.CONNECTED) {
            try {
                ProfileTaskCommandQuery.Builder builder = ProfileTaskCommandQuery.newBuilder();

                // sniffer info
                builder.setService(Config.Agent.SERVICE_NAME).setServiceInstance(Config.Agent.INSTANCE_NAME);

                // last command create time
                builder.setLastCommandTime(ServiceManager.INSTANCE.findService(ProfileTaskExecutionService.class)
                                                                  .getLastCommandCreateTime());

                Commands commands = profileTaskBlockingStub.withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS)
                                                           .getProfileTaskCommands(builder.build());

                ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
            } catch (Throwable t) {
                if (!(t instanceof StatusRuntimeException)) {
                    LOGGER.error(t, "Query profile task from backend fail.");
                    return;
                }
                final StatusRuntimeException statusRuntimeException = (StatusRuntimeException) t;
                if (statusRuntimeException.getStatus().getCode() == Status.Code.UNIMPLEMENTED) {
                    LOGGER.warn("Backend doesn't support profiling, profiling will be disabled");
                    if (getTaskListFuture != null) {
                        getTaskListFuture.cancel(true);
                    }

                    // stop snapshot sender
                    if (sendSnapshotFuture != null) {
                        sendSnapshotFuture.cancel(true);
                    }
                }
            }
        }
    }

    @Override
    public void prepare() {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() {
        sender = ServiceManager.INSTANCE.findService(ProfileSnapshotSender.class);

        if (Config.Profile.ACTIVE) {
            // query task list
            getTaskListFuture = Executors.newSingleThreadScheduledExecutor(
                new DefaultNamedThreadFactory("ProfileGetTaskService")
            ).scheduleWithFixedDelay(
                new RunnableWithExceptionProtection(
                    this,
                    t -> LOGGER.error("Query profile task list failure.", t)
                ), 0, Config.Collector.GET_PROFILE_TASK_INTERVAL, TimeUnit.SECONDS
            );

            sendSnapshotFuture = Executors.newSingleThreadScheduledExecutor(
                new DefaultNamedThreadFactory("ProfileSendSnapshotService")
            ).scheduleWithFixedDelay(
                new RunnableWithExceptionProtection(
                    () -> {
                        List<TracingThreadSnapshot> buffer = new ArrayList<>(Config.Profile.SNAPSHOT_TRANSPORT_BUFFER_SIZE);
                        snapshotQueue.drainTo(buffer);
                        if (!buffer.isEmpty()) {
                            sender.send(buffer);
                        }
                    },
                    t -> LOGGER.error("Profile segment snapshot upload failure.", t)
                ), 0, 500, TimeUnit.MILLISECONDS
            );
        }
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void shutdown() {
        if (getTaskListFuture != null) {
            getTaskListFuture.cancel(true);
        }

        if (sendSnapshotFuture != null) {
            sendSnapshotFuture.cancel(true);
        }
    }

    @Override
    public void statusChanged(GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            profileTaskBlockingStub = ProfileTaskGrpc.newBlockingStub(channel);
        } else {
            profileTaskBlockingStub = null;
        }
        this.status = status;
    }

    /**
     * add a new profiling snapshot, send to {@link #snapshotQueue}
     */
    public void addProfilingSnapshot(TracingThreadSnapshot snapshot) {
        snapshotQueue.add(snapshot);
    }

    /**
     * notify backend profile task has finish
     */
    public void notifyProfileTaskFinish(ProfileTask task) {
        try {
            final ProfileTaskFinishReport.Builder reportBuilder = ProfileTaskFinishReport.newBuilder();
            // sniffer info
            reportBuilder.setService(Config.Agent.SERVICE_NAME)
                         .setServiceInstance(Config.Agent.INSTANCE_NAME);
            // task info
            reportBuilder.setTaskId(task.getTaskId());

            // send data
            profileTaskBlockingStub.withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS)
                                   .reportTaskFinish(reportBuilder.build());
        } catch (Throwable e) {
            LOGGER.error(e, "Notify profile task finish to backend fail.");
        }
    }

}
