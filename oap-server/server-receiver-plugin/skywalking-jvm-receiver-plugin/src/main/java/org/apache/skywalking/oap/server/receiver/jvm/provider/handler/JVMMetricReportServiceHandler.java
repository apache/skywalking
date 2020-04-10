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
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.JVMMetricCollection;
import org.apache.skywalking.apm.network.language.agent.v3.JVMMetricReportServiceGrpc;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;

@Slf4j
public class JVMMetricReportServiceHandler extends JVMMetricReportServiceGrpc.JVMMetricReportServiceImplBase implements GRPCHandler {
    private final JVMSourceDispatcher jvmSourceDispatcher;

    public JVMMetricReportServiceHandler(ModuleManager moduleManager) {
        this.jvmSourceDispatcher = new JVMSourceDispatcher(moduleManager);
    }

    @Override
    public void collect(JVMMetricCollection request, StreamObserver<Commands> responseObserver) {
        if (log.isDebugEnabled()) {
            log.debug(
                "receive the jvm metrics from service instance, name: {}, instance: {}",
                request.getService(),
                request.getServiceInstance()
            );
        }

        request.getMetricsList().forEach(jvmMetric -> {
            jvmSourceDispatcher.sendMetric(request.getService(), request.getServiceInstance(), jvmMetric);
        });

        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }

}
