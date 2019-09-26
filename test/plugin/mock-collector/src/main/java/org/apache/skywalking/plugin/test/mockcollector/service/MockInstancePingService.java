package org.apache.skywalking.plugin.test.mockcollector.service;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.common.Commands;
import org.apache.skywalking.apm.network.register.v2.ServiceInstancePingGrpc;
import org.apache.skywalking.apm.network.register.v2.ServiceInstancePingPkg;
import org.apache.skywalking.plugin.test.mockcollector.entity.RegistryItem;
import org.apache.skywalking.plugin.test.mockcollector.entity.ValidateData;

public class MockInstancePingService extends ServiceInstancePingGrpc.ServiceInstancePingImplBase {

    @Override
    public void doPing(ServiceInstancePingPkg request, StreamObserver<Commands> responseObserver) {
        ValidateData.INSTANCE.getRegistryItem().registryHeartBeat(new RegistryItem.HeartBeat(request.getServiceInstanceId()));
        responseObserver.onNext(Commands.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
