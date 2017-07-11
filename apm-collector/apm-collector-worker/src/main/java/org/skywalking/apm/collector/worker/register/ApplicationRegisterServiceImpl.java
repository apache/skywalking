package org.skywalking.apm.collector.worker.register;

import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.AbstractWorker;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.WorkerRef;
import org.skywalking.apm.collector.worker.grpcserver.WorkerCaller;
import org.skywalking.apm.collector.worker.segment.SegmentReceiver;
import org.skywalking.apm.network.proto.Application;
import org.skywalking.apm.network.proto.ApplicationMapping;
import org.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;

/**
 * @author pengys5
 */
public class ApplicationRegisterServiceImpl extends ApplicationRegisterServiceGrpc.ApplicationRegisterServiceImplBase implements WorkerCaller {

    private Logger logger = LogManager.getFormatterLogger(ApplicationRegisterServiceImpl.class);

    private ClusterWorkerContext clusterWorkerContext;
    private WorkerRef segmentReceiverWorkRef;

    @Override public void preStart() throws ProviderNotFoundException {
        segmentReceiverWorkRef = clusterWorkerContext.findProvider(SegmentReceiver.WorkerRole.INSTANCE).create(AbstractWorker.noOwner());

    }

    @Override public void inject(ClusterWorkerContext clusterWorkerContext) {
        this.clusterWorkerContext = clusterWorkerContext;
    }

    @Override public void register(Application request, StreamObserver<ApplicationMapping> responseObserver) {

    }
}
