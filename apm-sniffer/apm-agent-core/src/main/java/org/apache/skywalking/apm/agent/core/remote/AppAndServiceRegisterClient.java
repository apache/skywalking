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

import io.grpc.ManagedChannel;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.dictionary.ApplicationDictionary;
import org.apache.skywalking.apm.agent.core.os.OSUtil;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.TracingContextListener;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.dictionary.OperationNameDictionary;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.proto.Application;
import org.apache.skywalking.apm.network.proto.ApplicationInstance;
import org.apache.skywalking.apm.network.proto.ApplicationInstanceHeartbeat;
import org.apache.skywalking.apm.network.proto.ApplicationInstanceMapping;
import org.apache.skywalking.apm.network.proto.ApplicationInstanceRecover;
import org.apache.skywalking.apm.network.proto.ApplicationMapping;
import org.apache.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;
import org.apache.skywalking.apm.network.proto.InstanceDiscoveryServiceGrpc;
import org.apache.skywalking.apm.network.proto.ServiceNameDiscoveryServiceGrpc;

/**
 * @author wusheng
 */
public class AppAndServiceRegisterClient implements BootService, GRPCChannelListener, Runnable, TracingContextListener {
    private static final ILog logger = LogManager.getLogger(AppAndServiceRegisterClient.class);
    private static final String PROCESS_UUID = UUID.randomUUID().toString().replaceAll("-", "");

    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;
    private volatile ApplicationRegisterServiceGrpc.ApplicationRegisterServiceBlockingStub applicationRegisterServiceBlockingStub;
    private volatile InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceBlockingStub instanceDiscoveryServiceBlockingStub;
    private volatile ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceBlockingStub serviceNameDiscoveryServiceBlockingStub;
    private volatile ScheduledFuture<?> applicationRegisterFuture;
    private volatile boolean needRegisterRecover = false;
    private volatile long lastSegmentTime = -1;

    @Override
    public void statusChanged(GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            ManagedChannel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getManagedChannel();
            applicationRegisterServiceBlockingStub = ApplicationRegisterServiceGrpc.newBlockingStub(channel);
            instanceDiscoveryServiceBlockingStub = InstanceDiscoveryServiceGrpc.newBlockingStub(channel);
            if (RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID != DictionaryUtil.nullValue()) {
                needRegisterRecover = true;
            }
            serviceNameDiscoveryServiceBlockingStub = ServiceNameDiscoveryServiceGrpc.newBlockingStub(channel);
        } else {
            applicationRegisterServiceBlockingStub = null;
            instanceDiscoveryServiceBlockingStub = null;
            serviceNameDiscoveryServiceBlockingStub = null;
        }
        this.status = status;
    }

    @Override
    public void beforeBoot() throws Throwable {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() throws Throwable {
        applicationRegisterFuture = Executors
            .newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("AppAndServiceRegisterClient"))
            .scheduleAtFixedRate(this, 0, Config.Collector.APP_AND_SERVICE_REGISTER_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    public void afterBoot() throws Throwable {
        TracingContext.ListenerManager.add(this);
    }

    @Override
    public void shutdown() throws Throwable {
        applicationRegisterFuture.cancel(true);
    }

    @Override
    public void run() {
        logger.debug("AppAndServiceRegisterClient running, status:{}.",status);
        boolean shouldTry = true;
        while (GRPCChannelStatus.CONNECTED.equals(status) && shouldTry) {
            shouldTry = false;
            try {
                if (RemoteDownstreamConfig.Agent.APPLICATION_ID == DictionaryUtil.nullValue()) {
                    if (applicationRegisterServiceBlockingStub != null) {
                        ApplicationMapping applicationMapping = applicationRegisterServiceBlockingStub.register(
                            Application.newBuilder().addApplicationCode(Config.Agent.APPLICATION_CODE).build());
                        if (applicationMapping.getApplicationCount() > 0) {
                            RemoteDownstreamConfig.Agent.APPLICATION_ID = applicationMapping.getApplication(0).getValue();
                            shouldTry = true;
                        }
                    }
                } else {
                    if (instanceDiscoveryServiceBlockingStub != null) {
                        if (RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID == DictionaryUtil.nullValue()) {

                            ApplicationInstanceMapping instanceMapping = instanceDiscoveryServiceBlockingStub.register(ApplicationInstance.newBuilder()
                                .setApplicationId(RemoteDownstreamConfig.Agent.APPLICATION_ID)
                                .setAgentUUID(PROCESS_UUID)
                                .setRegisterTime(System.currentTimeMillis())
                                .setOsinfo(OSUtil.buildOSInfo())
                                .build());
                            if (instanceMapping.getApplicationInstanceId() != DictionaryUtil.nullValue()) {
                                RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID
                                    = instanceMapping.getApplicationInstanceId();
                            }
                        } else {
                            if (needRegisterRecover) {
                                instanceDiscoveryServiceBlockingStub.registerRecover(ApplicationInstanceRecover.newBuilder()
                                    .setApplicationId(RemoteDownstreamConfig.Agent.APPLICATION_ID)
                                    .setApplicationInstanceId(RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID)
                                    .setRegisterTime(System.currentTimeMillis())
                                    .setOsinfo(OSUtil.buildOSInfo())
                                    .build());
                                needRegisterRecover = false;
                            } else {
                                if (lastSegmentTime - System.currentTimeMillis() > 60 * 1000) {
                                    instanceDiscoveryServiceBlockingStub.heartbeat(ApplicationInstanceHeartbeat.newBuilder()
                                        .setApplicationInstanceId(RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID)
                                        .setHeartbeatTime(System.currentTimeMillis())
                                        .build());
                                }
                            }

                            ApplicationDictionary.INSTANCE.syncRemoteDictionary(applicationRegisterServiceBlockingStub);
                            OperationNameDictionary.INSTANCE.syncRemoteDictionary(serviceNameDiscoveryServiceBlockingStub);
                        }
                    }
                }
            } catch (Throwable t) {
                logger.error(t, "AppAndServiceRegisterClient execute fail.");
                ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(t);
            }
        }
    }

    @Override
    public void afterFinished(TraceSegment traceSegment) {
        lastSegmentTime = System.currentTimeMillis();
    }
}
