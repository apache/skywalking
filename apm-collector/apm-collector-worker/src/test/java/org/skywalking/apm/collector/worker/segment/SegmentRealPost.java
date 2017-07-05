package org.skywalking.apm.collector.worker.segment;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.worker.segment.mock.SegmentMock;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.skywalking.apm.network.proto.UpstreamSegment;

/**
 * @author pengys5
 */
public class SegmentRealPost {

    private static Logger logger = LogManager.getFormatterLogger(SegmentRealPost.class);

    public static void main(String[] args) throws Exception {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 22800)
            .usePlaintext(true)
            .build();

        TraceSegmentServiceGrpc.TraceSegmentServiceStub stub = TraceSegmentServiceGrpc.newStub(channel);
        StreamObserver<UpstreamSegment> observer = stub.collect(new StreamObserver<Downstream>() {
            @Override public void onNext(Downstream downstream) {

            }

            @Override public void onError(Throwable throwable) {

            }

            @Override public void onCompleted() {

            }
        });

        List<UpstreamSegment> upstreamSegmentList = SegmentMock.mockPortalServiceSegment();
        logger.debug("upstreamSegmentList size: %s", upstreamSegmentList.size());
        upstreamSegmentList.forEach(upstreamSegment -> {
            observer.onNext(upstreamSegment);
        });
        observer.onCompleted();

        Thread.sleep(2000);
    }
}
