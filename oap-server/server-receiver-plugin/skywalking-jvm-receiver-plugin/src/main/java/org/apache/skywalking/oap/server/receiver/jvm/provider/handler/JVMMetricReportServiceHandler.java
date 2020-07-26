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
import org.apache.skywalking.oap.server.analyzer.provider.jvm.JVMSourceDispatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;

@Slf4j
public class JVMMetricReportServiceHandler extends JVMMetricReportServiceGrpc.JVMMetricReportServiceImplBase implements GRPCHandler {
    private final JVMSourceDispatcher jvmSourceDispatcher;
    private final NamingControl namingControl;

    public JVMMetricReportServiceHandler(ModuleManager moduleManager) {
        this.jvmSourceDispatcher = new JVMSourceDispatcher(moduleManager);
        this.namingControl = moduleManager.find(CoreModule.NAME)
                                          .provider()
                                          .getService(NamingControl.class);
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
        final JVMMetricCollection.Builder builder = request.toBuilder();
        builder.setService(namingControl.formatServiceName(builder.getService()));
        builder.setServiceInstance(namingControl.formatInstanceName(builder.getServiceInstance()));

        builder.getMetricsList().forEach(jvmMetric -> {
            jvmSourceDispatcher.sendMetric(builder.getService(), builder.getServiceInstance(), jvmMetric);
        });

        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }

}
