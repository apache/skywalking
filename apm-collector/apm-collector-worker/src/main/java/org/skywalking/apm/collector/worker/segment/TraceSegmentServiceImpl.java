package org.skywalking.apm.collector.worker.segment;

import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.AbstractWorker;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.WorkerInvokeException;
import org.skywalking.apm.collector.actor.WorkerRef;
import org.skywalking.apm.collector.worker.grpcserver.WorkerCaller;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.skywalking.apm.network.proto.UpstreamSegment;

/**
 * @author pengys5
 */
public class TraceSegmentServiceImpl extends TraceSegmentServiceGrpc.TraceSegmentServiceImplBase implements WorkerCaller {

    private Logger logger = LogManager.getFormatterLogger(TraceSegmentServiceImpl.class);

    private ClusterWorkerContext clusterWorkerContext;
    private WorkerRef segmentReceiverWorkRef;

    @Override public void preStart() throws ProviderNotFoundException {
        segmentReceiverWorkRef = clusterWorkerContext.findProvider(SegmentReceiver.WorkerRole.INSTANCE).create(AbstractWorker.noOwner());
    }

    @Override public StreamObserver<UpstreamSegment> collect(StreamObserver<Downstream> responseObserver) {
        return new StreamObserver<UpstreamSegment>() {
            @Override public void onNext(UpstreamSegment segment) {
                if (logger.isDebugEnabled()) {
                    StringBuffer globalTraceIds = new StringBuffer();
                    logger.debug("global trace ids count: %s", segment.getGlobalTraceIdsList().size());
                    segment.getGlobalTraceIdsList().forEach(globalTraceId -> {
                        globalTraceIds.append(globalTraceId).append(",");
                    });
                    logger.debug("receive segment, global trace ids: %s, segment byte size: %s", globalTraceIds, segment.getSegment().size());
                    try {
                        segmentReceiverWorkRef.tell(segment);
                    } catch (WorkerInvokeException e) {
                        onError(e);
                    }
                }
            }

            @Override public void onError(Throwable throwable) {
                logger.error(throwable.getMessage(), throwable);
            }

            @Override public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    @Override public void inject(ClusterWorkerContext clusterWorkerContext) {
        this.clusterWorkerContext = clusterWorkerContext;
    }
}
