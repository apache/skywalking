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
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.dictionary.NetworkAddressDictionary;
import org.apache.skywalking.apm.agent.core.dictionary.OperationNameDictionary;
import org.apache.skywalking.apm.agent.core.listener.ResetStatus;
import org.apache.skywalking.apm.agent.core.listener.Reseter;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.os.OSUtil;
import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.apm.network.register.ServiceInstancePingGrpc;
import org.apache.skywalking.apm.network.register.ServiceInstancePingPkg;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * @author wusheng
 */
@DefaultImplementor
public class AppAndServiceRegisterClient implements BootService, Runnable, GRPCChannelListener {
    private static final ILog logger = LogManager.getLogger(AppAndServiceRegisterClient.class);

    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;
    private volatile ApplicationRegisterServiceGrpc.ApplicationRegisterServiceBlockingStub applicationRegisterServiceBlockingStub;
    private volatile InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceBlockingStub instanceDiscoveryServiceBlockingStub;
    private volatile ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceBlockingStub serviceNameDiscoveryServiceBlockingStub;
    private volatile NetworkAddressRegisterServiceGrpc.NetworkAddressRegisterServiceBlockingStub networkAddressRegisterServiceBlockingStub;
    private volatile ServiceInstancePingGrpc.ServiceInstancePingBlockingStub serviceInstancePingBlockingStub;
    private volatile ScheduledFuture<?> applicationRegisterFuture;
    private volatile long lastSegmentTime = -1;

    @Override
    public void statusChanged(GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            applicationRegisterServiceBlockingStub = ApplicationRegisterServiceGrpc.newBlockingStub(channel);
            instanceDiscoveryServiceBlockingStub = InstanceDiscoveryServiceGrpc.newBlockingStub(channel);
            serviceNameDiscoveryServiceBlockingStub = ServiceNameDiscoveryServiceGrpc.newBlockingStub(channel);
            networkAddressRegisterServiceBlockingStub = NetworkAddressRegisterServiceGrpc.newBlockingStub(channel);
            serviceInstancePingBlockingStub = ServiceInstancePingGrpc.newBlockingStub(channel);
        } else {
            applicationRegisterServiceBlockingStub = null;
            instanceDiscoveryServiceBlockingStub = null;
            serviceNameDiscoveryServiceBlockingStub = null;
        }
        this.status = status;
    }

    @Override
    public void prepare() throws Throwable {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() throws Throwable {
        applicationRegisterFuture = Executors
            .newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("AppAndServiceRegisterClient"))
            .scheduleAtFixedRate(new RunnableWithExceptionProtection(this, new RunnableWithExceptionProtection.CallbackWhenException() {
                @Override
                public void handle(Throwable t) {
                    logger.error("unexpected exception.", t);
                }
            }), 0, Config.Collector.APP_AND_SERVICE_REGISTER_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    public void onComplete() throws Throwable {
    }

    @Override
    public void shutdown() throws Throwable {
        applicationRegisterFuture.cancel(true);
    }

    @Override
    public void run() {
        logger.debug("AppAndServiceRegisterClient running, status:{}.", status);
        boolean shouldTry = true;
        String instanceUUID = StringUtil.isEmpty(Config.Agent.INSTANCE_UUID) ? UUID.randomUUID().toString().replaceAll("-", "") : Config.Agent.INSTANCE_UUID;
        while (GRPCChannelStatus.CONNECTED.equals(status) && shouldTry) {
            shouldTry = false;
            try {
                if (RemoteDownstreamConfig.Agent.APPLICATION_ID == DictionaryUtil.nullValue()) {
                    if (applicationRegisterServiceBlockingStub != null) {
                        ApplicationMapping applicationMapping = applicationRegisterServiceBlockingStub.applicationCodeRegister(
                            Application.newBuilder().setApplicationCode(Config.Agent.APPLICATION_CODE).build());
                        if (applicationMapping != null) {
                            RemoteDownstreamConfig.Agent.APPLICATION_ID = applicationMapping.getApplication().getValue();
                            Reseter.INSTANCE.reportToRegisterFile();
                            shouldTry = true;
                        }
                    }
                } else {
                    if (instanceDiscoveryServiceBlockingStub != null) {
                        if (RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID == DictionaryUtil.nullValue()) {

                            ApplicationInstanceMapping instanceMapping = instanceDiscoveryServiceBlockingStub.registerInstance(ApplicationInstance.newBuilder()
                                .setApplicationId(RemoteDownstreamConfig.Agent.APPLICATION_ID)
                                .setAgentUUID(instanceUUID)
                                .setRegisterTime(System.currentTimeMillis())
                                .setOsinfo(OSUtil.buildOSInfo())
                                .build());
                            if (instanceMapping.getApplicationInstanceId() != DictionaryUtil.nullValue()) {
                                RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID
                                    = instanceMapping.getApplicationInstanceId();
                                Reseter.INSTANCE.setStatus(ResetStatus.OFF).reportToRegisterFile();
                            }
                        } else {
                            if (lastSegmentTime - System.currentTimeMillis() > 60 * 1000) {
                                serviceInstancePingBlockingStub.doPing(ServiceInstancePingPkg.newBuilder()
                                    .setServiceInstanceId(RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID)
                                    .setServiceInstanceUUID(instanceUUID)
                                    .setTime(System.currentTimeMillis())
                                    .build());
                            }

                            NetworkAddressDictionary.INSTANCE.syncRemoteDictionary(networkAddressRegisterServiceBlockingStub);
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
}
