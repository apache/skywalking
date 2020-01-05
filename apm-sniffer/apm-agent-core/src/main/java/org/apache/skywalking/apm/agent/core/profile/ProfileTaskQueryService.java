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
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.network.common.Commands;
import org.apache.skywalking.apm.network.language.profile.ProfileTaskCommandQuery;
import org.apache.skywalking.apm.network.language.profile.ProfileTaskGrpc;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

/**
 * sniffer will check has new profile task list every {@link Config.Collector#GET_PROFILE_TASK_INTERVAL} second.
 *
 * @author MrPro
 */
@DefaultImplementor
public class ProfileTaskQueryService implements BootService, Runnable, GRPCChannelListener {
    private static final ILog logger = LogManager.getLogger(ProfileTaskQueryService.class);

    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;
    private volatile ProfileTaskGrpc.ProfileTaskBlockingStub profileTaskBlockingStub;
    private volatile ScheduledFuture<?> getTaskListFuture;

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
                    logger.error(t, "query profile task from Collector fail.", t);
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
            getTaskListFuture = Executors.newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("ProfileGetTaskService"))
                    .scheduleWithFixedDelay(this, 0, Config.Collector.GET_PROFILE_TASK_INTERVAL, TimeUnit.SECONDS);
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
}
