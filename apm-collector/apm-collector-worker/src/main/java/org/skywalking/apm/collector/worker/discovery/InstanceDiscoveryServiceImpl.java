package org.skywalking.apm.collector.worker.discovery;

import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.worker.grpcserver.WorkerCaller;
import org.skywalking.apm.network.proto.ApplicationInstance;
import org.skywalking.apm.network.proto.ApplicationInstanceMapping;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.InstanceDiscoveryServiceGrpc;

/**
 * @author pengys5
 */
public class InstanceDiscoveryServiceImpl extends InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceImplBase implements WorkerCaller {

    @Override public void preStart() throws ProviderNotFoundException {
    }

    @Override public void inject(ClusterWorkerContext clusterWorkerContext) {
    }

    @Override
    public void register(ApplicationInstance request, StreamObserver<ApplicationInstanceMapping> responseObserver) {
        super.register(request, responseObserver);
    }

    @Override
    public void registerRecover(ApplicationInstanceMapping request, StreamObserver<Downstream> responseObserver) {
        super.registerRecover(request, responseObserver);
    }
}
