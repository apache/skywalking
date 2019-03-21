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
import org.apache.skywalking.apm.network.common.Commands;
import org.apache.skywalking.apm.network.language.agent.v2.CLRMetricCollection;
import org.apache.skywalking.apm.network.language.agent.v2.CLRMetricReportServiceGrpc;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.util.TimeBucketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author liuhaoyang
 **/
public class CLRMetricReportServiceHandler extends CLRMetricReportServiceGrpc.CLRMetricReportServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(CLRMetricReportServiceHandler.class);

    private final CLRSourceDispatcher clrSourceDispatcher;

    public CLRMetricReportServiceHandler(ModuleManager moduleManager) {
        clrSourceDispatcher = new CLRSourceDispatcher(moduleManager);
    }

    @Override public void collect(CLRMetricCollection request, StreamObserver<Commands> responseObserver) {
        int serviceInstanceId = request.getServiceInstanceId();

        if (logger.isDebugEnabled()) {
            logger.debug("receive the clr metric from service instance, id: {}", serviceInstanceId);
        }

        request.getMetricsList().forEach(metric -> {
            long minuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(metric.getTime());
            clrSourceDispatcher.sendMetric(serviceInstanceId, minuteTimeBucket, metric);
        });

        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }
}
