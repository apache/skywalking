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

package org.apache.skywalking.apm.agent.core.meter;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.agent.core.remote.GRPCStreamServiceStatus;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterReportServiceGrpc;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

/**
 * MeterSender collects the values of registered meter instances, and sends to the backend.
 */
@DefaultImplementor
public class MeterSender implements BootService, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(MeterSender.class);

    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;
    private volatile MeterReportServiceGrpc.MeterReportServiceStub meterReportServiceStub;

    @Override
    public void prepare() {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() {

    }

    public void send(Map<MeterId, BaseMeter> meterMap, MeterService meterService) {
        if (status == GRPCChannelStatus.CONNECTED) {
            StreamObserver<MeterData> reportStreamObserver = null;
            final GRPCStreamServiceStatus status = new GRPCStreamServiceStatus(false);
            try {
                reportStreamObserver = meterReportServiceStub.withDeadlineAfter(
                    GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS
                ).collect(new StreamObserver<Commands>() {
                    @Override
                    public void onNext(Commands commands) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        status.finished();
                        if (LOGGER.isErrorEnable()) {
                            LOGGER.error(throwable, "Send meters to collector fail with a grpc internal exception.");
                        }
                        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(throwable);
                    }

                    @Override
                    public void onCompleted() {
                        status.finished();
                    }
                });

                final StreamObserver<MeterData> reporter = reportStreamObserver;
                transform(meterMap, meterData -> reporter.onNext(meterData));
            } catch (Throwable e) {
                if (!(e instanceof StatusRuntimeException)) {
                    LOGGER.error(e, "Report meters to backend fail.");
                    return;
                }
                final StatusRuntimeException statusRuntimeException = (StatusRuntimeException) e;
                if (statusRuntimeException.getStatus().getCode() == Status.Code.UNIMPLEMENTED) {
                    LOGGER.warn("Backend doesn't support meter, it will be disabled");

                    meterService.shutdown();
                }
            } finally {
                if (reportStreamObserver != null) {
                    reportStreamObserver.onCompleted();
                }
                status.wait4Finish();
            }
        }
    }

    protected void transform(final Map<MeterId, BaseMeter> meterMap,
                             final Consumer<MeterData> consumer) {
        // build and report meters
        boolean hasSendMachineInfo = false;
        for (BaseMeter meter : meterMap.values()) {
            final MeterData.Builder dataBuilder = meter.transform();
            if (dataBuilder == null) {
                continue;
            }

            // only send the service base info at the first data
            if (!hasSendMachineInfo) {
                dataBuilder.setService(Config.Agent.SERVICE_NAME);
                dataBuilder.setServiceInstance(Config.Agent.INSTANCE_NAME);
                dataBuilder.setTimestamp(System.currentTimeMillis());
                hasSendMachineInfo = true;
            }

            consumer.accept(dataBuilder.build());
        }
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void statusChanged(final GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            meterReportServiceStub = MeterReportServiceGrpc.newStub(channel);
        } else {
            meterReportServiceStub = null;
        }
        this.status = status;
    }
}
