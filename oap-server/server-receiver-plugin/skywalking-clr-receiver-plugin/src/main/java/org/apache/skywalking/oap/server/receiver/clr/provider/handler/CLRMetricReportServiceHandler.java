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

package org.apache.skywalking.oap.server.receiver.clr.provider.handler;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.CLRMetricCollection;
import org.apache.skywalking.apm.network.language.agent.v3.CLRMetricReportServiceGrpc;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;

@Slf4j
public class CLRMetricReportServiceHandler extends CLRMetricReportServiceGrpc.CLRMetricReportServiceImplBase implements GRPCHandler {
    private final CLRSourceDispatcher clrSourceDispatcher;
    private final NamingControl namingControl;

    public CLRMetricReportServiceHandler(ModuleManager moduleManager) {
        clrSourceDispatcher = new CLRSourceDispatcher(moduleManager);
        this.namingControl = moduleManager.find(CoreModule.NAME)
                                          .provider()
                                          .getService(NamingControl.class);
    }

    @Override
    public void collect(CLRMetricCollection request, StreamObserver<Commands> responseObserver) {
        if (log.isDebugEnabled()) {
            log.debug("receive the clr metrics from service instance, id: {}", request.getServiceInstance());
        }

        final CLRMetricCollection.Builder builder = request.toBuilder();
        builder.setService(namingControl.formatServiceName(builder.getService()));
        builder.setServiceInstance(namingControl.formatInstanceName(builder.getServiceInstance()));

        request.getMetricsList().forEach(metrics -> {
            long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(metrics.getTime());
            clrSourceDispatcher.sendMetric(
                request.getService(), request.getServiceInstance(), minuteTimeBucket, metrics);
        });

        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }
}
