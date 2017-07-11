package org.skywalking.apm.collector.worker.discovery;

import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.worker.grpcserver.WorkerCaller;
import org.skywalking.apm.network.proto.ServiceNameCollection;
import org.skywalking.apm.network.proto.ServiceNameDiscoveryServiceGrpc;
import org.skywalking.apm.network.proto.ServiceNameMappingCollection;

/**
 * @author pengys5
 */
public class ServiceNameDisCoveryServiceImpl extends ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceImplBase implements WorkerCaller {
    @Override public void preStart() throws ProviderNotFoundException {
    }

    @Override public void inject(ClusterWorkerContext clusterWorkerContext) {
    }

    @Override public void discovery(ServiceNameCollection request,
        StreamObserver<ServiceNameMappingCollection> responseObserver) {
        super.discovery(request, responseObserver);
    }
}
