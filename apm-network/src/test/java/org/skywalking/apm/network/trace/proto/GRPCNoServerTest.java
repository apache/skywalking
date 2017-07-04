package org.skywalking.apm.network.trace.proto;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.Assert;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.skywalking.apm.network.proto.UpstreamSegment;

/**
 * @author wusheng
 */
public class GRPCNoServerTest {
    public static void main(String[] args) throws InterruptedException {
        ManagedChannelBuilder<?> channelBuilder =
            NettyChannelBuilder.forAddress("127.0.0.1", 8080)
                .nameResolverFactory(new DnsNameResolverProvider())
                .maxInboundMessageSize(1024 * 1024 * 50)
                .usePlaintext(true);
        ManagedChannel channel = channelBuilder.build();
        TraceSegmentServiceGrpc.TraceSegmentServiceStub serviceStub = TraceSegmentServiceGrpc.newStub(channel);
        final Status[] status = {null};
        StreamObserver<UpstreamSegment> streamObserver = serviceStub.collect(new StreamObserver<Downstream>() {
            @Override public void onNext(Downstream value) {

            }

            @Override public void onError(Throwable t) {
                status[0] = ((StatusRuntimeException)t).getStatus();
            }

            @Override public void onCompleted() {

            }
        });

        streamObserver.onNext(null);
        streamObserver.onCompleted();

        Thread.sleep(2 * 1000);

        Assert.assertEquals(status[0].getCode(), Status.UNAVAILABLE.getCode());
    }
}
