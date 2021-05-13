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
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.agent.core.remote.GRPCStreamServiceStatus;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskGrpc;
import org.apache.skywalking.apm.network.language.profile.v3.ThreadSnapshot;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

/**
 * send segment snapshot
 */
@DefaultImplementor
public class ProfileSnapshotSender implements BootService, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(ProfileSnapshotSender.class);

    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;

    private volatile ProfileTaskGrpc.ProfileTaskStub profileTaskStub;

    @Override
    public void prepare() throws Throwable {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void statusChanged(final GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            profileTaskStub = ProfileTaskGrpc.newStub(channel);
        } else {
            profileTaskStub = null;
        }
        this.status = status;
    }

    public void send(List<TracingThreadSnapshot> buffer) {
        if (status == GRPCChannelStatus.CONNECTED) {
            try {
                final GRPCStreamServiceStatus status = new GRPCStreamServiceStatus(false);
                StreamObserver<ThreadSnapshot> snapshotStreamObserver = profileTaskStub.withDeadlineAfter(
                    GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS
                ).collectSnapshot(
                    new StreamObserver<Commands>() {
                        @Override
                        public void onNext(
                            Commands commands) {
                        }

                        @Override
                        public void onError(
                            Throwable throwable) {
                            status.finished();
                            if (LOGGER.isErrorEnable()) {
                                LOGGER.error(
                                    throwable,
                                    "Send profile segment snapshot to collector fail with a grpc internal exception."
                                );
                            }
                            ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(throwable);
                        }

                        @Override
                        public void onCompleted() {
                            status.finished();
                        }
                    }
                );
                for (TracingThreadSnapshot snapshot : buffer) {
                    final ThreadSnapshot transformSnapshot = snapshot.transform();
                    snapshotStreamObserver.onNext(transformSnapshot);
                }

                snapshotStreamObserver.onCompleted();
                status.wait4Finish();
            } catch (Throwable t) {
                LOGGER.error(t, "Send profile segment snapshot to backend fail.");
            }
        }
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {

    }
}