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

package org.apache.skywalking.oap.server.receiver.register.provider.handler.v8.grpc;

import com.google.gson.JsonObject;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.management.v3.InstancePingPkg;
import org.apache.skywalking.apm.network.management.v3.InstanceProperties;
import org.apache.skywalking.apm.network.management.v3.ManagementServiceGrpc;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceUpdate;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;

public class ManagementServiceHandler extends ManagementServiceGrpc.ManagementServiceImplBase implements GRPCHandler {
    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;

    public ManagementServiceHandler(ModuleManager moduleManager) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        this.namingControl = moduleManager.find(CoreModule.NAME)
                                          .provider()
                                          .getService(NamingControl.class);
    }

    @Override
    public void reportInstanceProperties(final InstanceProperties request,
                                         final StreamObserver<Commands> responseObserver) {
        ServiceInstanceUpdate serviceInstanceUpdate = new ServiceInstanceUpdate();
        final String serviceName = namingControl.formatServiceName(request.getService());
        final String instanceName = namingControl.formatInstanceName(request.getServiceInstance());
        serviceInstanceUpdate.setServiceId(IDManager.ServiceID.buildId(serviceName, NodeType.Normal));
        serviceInstanceUpdate.setName(instanceName);

        JsonObject properties = new JsonObject();
        List<String> ipv4List = new ArrayList<>();
        request.getPropertiesList().forEach(prop -> {
            if (InstanceTraffic.PropertyUtil.IPV4.equals(prop.getKey())) {
                ipv4List.add(prop.getValue());
            } else {
                properties.addProperty(prop.getKey(), prop.getValue());
            }
        });
        properties.addProperty(InstanceTraffic.PropertyUtil.IPV4S, ipv4List.stream().collect(Collectors.joining(",")));
        serviceInstanceUpdate.setProperties(properties);
        serviceInstanceUpdate.setTimeBucket(
            TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Minute));
        sourceReceiver.receive(serviceInstanceUpdate);

        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void keepAlive(final InstancePingPkg request, final StreamObserver<Commands> responseObserver) {
        final long timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Minute);
        final String serviceName = namingControl.formatServiceName(request.getService());
        final String instanceName = namingControl.formatInstanceName(request.getServiceInstance());

        ServiceInstanceUpdate serviceInstanceUpdate = new ServiceInstanceUpdate();
        serviceInstanceUpdate.setServiceId(IDManager.ServiceID.buildId(serviceName, NodeType.Normal));
        serviceInstanceUpdate.setName(instanceName);
        serviceInstanceUpdate.setTimeBucket(timeBucket);
        sourceReceiver.receive(serviceInstanceUpdate);

        ServiceMeta serviceMeta = new ServiceMeta();
        serviceMeta.setName(serviceName);
        serviceMeta.setNodeType(NodeType.Normal);
        serviceMeta.setTimeBucket(timeBucket);
        sourceReceiver.receive(serviceMeta);

        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }
}
