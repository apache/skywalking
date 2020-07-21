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
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.meter.transform.MeterTransformer;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.agent.core.remote.GRPCStreamServiceStatus;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterReportServiceGrpc;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

@DefaultImplementor
public class MeterService implements BootService, Runnable, GRPCChannelListener {
    private static final ILog logger = LogManager.getLogger(MeterService.class);

    // all meters
    private final ConcurrentHashMap<MeterId, MeterTransformer> meterMap = new ConcurrentHashMap<>();

    // channel status
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;

    // gRPC stub
    private volatile MeterReportServiceGrpc.MeterReportServiceStub meterReportServiceStub;

    // report meters
    private volatile ScheduledFuture<?> reportMeterFuture;

    /**
     * Register the meterTransformer
     */
    public <T extends MeterTransformer> void register(T meterTransformer) {
        if (meterTransformer == null) {
            return;
        }
        if (meterMap.size() >= Config.Meter.MAX_METER_SIZE) {
            logger.warn("Already out of the meter system max size, will not report. meter name:{}", meterTransformer.getName());
            return;
        }

        meterMap.putIfAbsent(meterTransformer.getId(), meterTransformer);
    }

    @Override
    public void prepare() throws Throwable {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() throws Throwable {
        if (Config.Meter.ACTIVE) {
            reportMeterFuture = Executors.newSingleThreadScheduledExecutor(
                new DefaultNamedThreadFactory("MeterReportService")
            ).scheduleWithFixedDelay(new RunnableWithExceptionProtection(
                this,
                t -> logger.error("Report meters failure.", t)
            ), 0, Config.Meter.REPORT_INTERVAL, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onComplete() throws Throwable {
    }

    @Override
    public void shutdown() throws Throwable {
        if (reportMeterFuture != null) {
            reportMeterFuture.cancel(true);
        }
        // clear all of the meter report
        meterMap.clear();
    }

    @Override
    public void run() {
        if (status != GRPCChannelStatus.CONNECTED || meterMap.isEmpty()) {
            return;
        }
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
                    if (logger.isErrorEnable()) {
                        logger.error(throwable, "Send meters to collector fail with a grpc internal exception.");
                    }
                    ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(throwable);
                }

                @Override
                public void onCompleted() {
                    status.finished();
                }
            });

            // build and report meters
            boolean hasSendMachineInfo = false;
            for (MeterTransformer meterTransformer : meterMap.values()) {
                final MeterData.Builder dataBuilder = meterTransformer.transform();
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

                reportStreamObserver.onNext(dataBuilder.build());
            }
        } catch (Throwable e) {
            if (!(e instanceof StatusRuntimeException)) {
                logger.error(e, "Report meters to backend fail.");
                return;
            }
            final StatusRuntimeException statusRuntimeException = (StatusRuntimeException) e;
            if (statusRuntimeException.getStatus().getCode() == Status.Code.UNIMPLEMENTED) {
                logger.warn("Backend doesn't support meter, it will be disabled");
                if (reportMeterFuture != null) {
                    reportMeterFuture.cancel(true);
                }
            }
        } finally {
            if (reportStreamObserver != null) {
                reportStreamObserver.onCompleted();
            }
            status.wait4Finish();
        }

    }

    @Override
    public void statusChanged(GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            meterReportServiceStub = MeterReportServiceGrpc.newStub(channel);
        } else {
            meterReportServiceStub = null;
        }
        this.status = status;
    }

}
