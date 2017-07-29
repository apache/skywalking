package org.skywalking.apm.collector.agentstream.grpc.handler;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.segment.SegmentParse;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.skywalking.apm.network.proto.UniqueId;
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
                logger.debug("receive segment");
                SegmentParse segmentParse = new SegmentParse();
                try {
                    List<UniqueId> traceIds = segment.getGlobalTraceIdsList();
                    TraceSegmentObject segmentObject = TraceSegmentObject.parseFrom(segment.getSegment());
                    segmentParse.parse(traceIds, segmentObject);
                } catch (InvalidProtocolBufferException e) {
                    logger.error(e.getMessage(), e);
                }
            }

            @Override public void onError(Throwable throwable) {
                logger.error(throwable.getMessage(), throwable);
            }

            @Override public void onCompleted() {
                responseObserver.onNext(Downstream.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
