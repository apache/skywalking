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

package org.apache.skywalking.oap.server.receiver.jvm.provider.handler;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.language.agent.Downstream;
import org.apache.skywalking.apm.network.language.agent.JVMMetrics;
import org.apache.skywalking.apm.network.language.agent.JVMMetricsServiceGrpc;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.util.TimeBucketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class JVMMetricsServiceHandler extends JVMMetricsServiceGrpc.JVMMetricsServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(JVMMetricsServiceHandler.class);

    private final JVMSourceDispatcher jvmSourceDispatcher;

    public JVMMetricsServiceHandler(ModuleManager moduleManager) {
        this.jvmSourceDispatcher = new JVMSourceDispatcher(moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class));
    }

    @Override public void collect(JVMMetrics request, StreamObserver<Downstream> responseObserver) {
        int serviceInstanceId = request.getApplicationInstanceId();

        if (logger.isDebugEnabled()) {
            logger.debug("receive the jvm metric from service instance, id: {}", serviceInstanceId);
        }

        request.getMetricsList().forEach(metric -> {
            long minuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(metric.getTime());
            jvmSourceDispatcher.sendMetric(serviceInstanceId, minuteTimeBucket, metric);
        });

        responseObserver.onNext(Downstream.newBuilder().build());
        responseObserver.onCompleted();
    }

}
