package org.skywalking.apm.collector.agentregister.grpc.handler;

import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.ServiceNameCollection;
import org.skywalking.apm.network.proto.ServiceNameDiscoveryServiceGrpc;
import org.skywalking.apm.network.proto.ServiceNameMappingCollection;

/**
 * @author pengys5
 */
public class ServiceNameDiscoveryServiceHandler extends ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceImplBase implements GRPCHandler {

    @Override public void discovery(ServiceNameCollection request,
        StreamObserver<ServiceNameMappingCollection> responseObserver) {
        super.discovery(request, responseObserver);
    }
}
