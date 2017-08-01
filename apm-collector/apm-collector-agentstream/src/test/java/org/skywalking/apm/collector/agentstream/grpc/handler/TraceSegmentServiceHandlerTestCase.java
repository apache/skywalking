package org.skywalking.apm.collector.agentstream.grpc.handler;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.KeyWithStringValue;
import org.skywalking.apm.network.proto.LogMessage;
import org.skywalking.apm.network.proto.SpanLayer;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.SpanType;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.TraceSegmentReference;
import org.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.skywalking.apm.network.proto.UniqueId;
import org.skywalking.apm.network.proto.UpstreamSegment;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class TraceSegmentServiceHandlerTestCase {

    private final Logger logger = LoggerFactory.getLogger(TraceSegmentServiceHandlerTestCase.class);

    private TraceSegmentServiceGrpc.TraceSegmentServiceStub stub;

    public void testCollect() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();
        stub = TraceSegmentServiceGrpc.newStub(channel);

        StreamObserver<UpstreamSegment> streamObserver = stub.collect(new StreamObserver<Downstream>() {
            @Override public void onNext(Downstream downstream) {
            }

            @Override public void onError(Throwable throwable) {
                logger.error(throwable.getMessage(), throwable);
            }

            @Override public void onCompleted() {

            }
        });

        UpstreamSegment.Builder builder = UpstreamSegment.newBuilder();
        buildGlobalTraceIds(builder);
        buildSegment(builder);

        streamObserver.onNext(builder.build());
        streamObserver.onCompleted();

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
        }
    }

    private void buildGlobalTraceIds(UpstreamSegment.Builder builder) {
        UniqueId.Builder builder1 = UniqueId.newBuilder();
        builder1.addIdParts(100);
        builder1.addIdParts(100);
        builder1.addIdParts(100);
        builder.addGlobalTraceIds(builder1.build());
    }

    private void buildSegment(UpstreamSegment.Builder builder) {
        long now = System.currentTimeMillis();

        TraceSegmentObject.Builder segmentBuilder = TraceSegmentObject.newBuilder();
        segmentBuilder.setApplicationId(2);
        segmentBuilder.setApplicationInstanceId(2);
        segmentBuilder.setTraceSegmentId(UniqueId.newBuilder().addIdParts(200).addIdParts(200).addIdParts(200).build());

        SpanObject.Builder span_0 = SpanObject.newBuilder();
        span_0.setSpanId(0);
        span_0.setOperationName("/dubbox-case/case/dubbox-rest");
        span_0.setOperationNameId(0);
        span_0.setParentSpanId(-1);
        span_0.setSpanLayer(SpanLayer.Http);
        span_0.setStartTime(now);
        span_0.setEndTime(now + 100000);
        span_0.setComponentId(ComponentsDefine.TOMCAT.getId());
        span_0.setIsError(false);
        span_0.setSpanType(SpanType.Entry);
        span_0.setPeerId(2);
        span_0.setPeer("localhost:8082");

        LogMessage.Builder log_0 = LogMessage.newBuilder();
        log_0.setTime(now);
        log_0.addData(KeyWithStringValue.newBuilder().setKey("log1").setValue("value1"));
        log_0.addData(KeyWithStringValue.newBuilder().setKey("log2").setValue("value2"));
        log_0.addData(KeyWithStringValue.newBuilder().setKey("log3").setValue("value3"));
        span_0.addLogs(log_0.build());

        span_0.addTags(KeyWithStringValue.newBuilder().setKey("tag1").setValue("value1"));
        span_0.addTags(KeyWithStringValue.newBuilder().setKey("tag2").setValue("value2"));
        span_0.addTags(KeyWithStringValue.newBuilder().setKey("tag3").setValue("value3"));
        segmentBuilder.addSpans(span_0);

        TraceSegmentReference.Builder ref_0 = TraceSegmentReference.newBuilder();
        ref_0.setEntryServiceId(1);
        ref_0.setEntryServiceName("ServiceName");
        ref_0.setNetworkAddress("localhost:8081");
        ref_0.setNetworkAddressId(1);
        ref_0.setParentApplicationInstanceId(1);
        ref_0.setParentServiceId(1);
        ref_0.setParentServiceName("");
        ref_0.setParentSpanId(2);
        ref_0.setParentTraceSegmentId(UniqueId.newBuilder().addIdParts(100).addIdParts(100).addIdParts(100).build());
        segmentBuilder.addRefs(ref_0);

        builder.setSegment(segmentBuilder.build().toByteString());
    }
}
