package org.skywalking.apm.collector.agentstream.grpc.handler;

import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.skywalking.apm.network.proto.UpstreamSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class TraceSegmentServiceHandler extends TraceSegmentServiceGrpc.TraceSegmentServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(TraceSegmentServiceHandler.class);

    @Override public StreamObserver<UpstreamSegment> collect(StreamObserver<Downstream> responseObserver) {
        return new StreamObserver<UpstreamSegment>() {
            @Override public void onNext(UpstreamSegment segment) {
            }

            @Override public void onError(Throwable throwable) {
                logger.error(throwable.getMessage(), throwable);
            }

            @Override public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
