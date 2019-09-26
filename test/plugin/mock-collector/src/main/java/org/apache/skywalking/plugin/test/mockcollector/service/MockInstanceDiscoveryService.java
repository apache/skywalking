package org.apache.skywalking.plugin.test.mockcollector.service;

import io.grpc.stub.StreamObserver;

import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.plugin.test.mockcollector.entity.RegistryItem;
import org.apache.skywalking.plugin.test.mockcollector.entity.ValidateData;

public class MockInstanceDiscoveryService extends InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceImplBase {


    @Override
    public void heartbeat(ApplicationInstanceHeartbeat request, StreamObserver<Downstream> responseObserver) {
        ValidateData.INSTANCE.getRegistryItem().registryHeartBeat(new RegistryItem.HeartBeat(request.getApplicationInstanceId()));
        responseObserver.onNext(Downstream.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void registerInstance(ApplicationInstance request,
                                 StreamObserver<ApplicationInstanceMapping> responseObserver) {
        int instanceId = Sequences.INSTANCE_SEQUENCE.incrementAndGet();
        ValidateData.INSTANCE.getRegistryItem().registryInstance(new RegistryItem.Instance(request.getApplicationId(), instanceId));

        responseObserver.onNext(ApplicationInstanceMapping.newBuilder().setApplicationId(request.getApplicationId())
                .setApplicationInstanceId(instanceId).build());
        responseObserver.onCompleted();
    }
}
