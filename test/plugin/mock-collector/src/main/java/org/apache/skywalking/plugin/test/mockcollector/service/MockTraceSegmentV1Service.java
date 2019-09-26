package org.apache.skywalking.plugin.test.mockcollector.service;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.plugin.test.mockcollector.entity.Segment;
import org.apache.skywalking.plugin.test.mockcollector.entity.ValidateData;
import org.apache.skywalking.plugin.test.mockcollector.entity.Span;

public class MockTraceSegmentV1Service extends TraceSegmentServiceGrpc.TraceSegmentServiceImplBase {

    private Logger logger = LogManager.getLogger(MockTraceSegmentService.class);

    @Override
    public StreamObserver<UpstreamSegment> collect(final StreamObserver<Downstream> responseObserver) {
        return new StreamObserver<UpstreamSegment>() {
            @Override public void onNext(UpstreamSegment value) {
                try {
                    TraceSegmentObject traceSegmentObject = TraceSegmentObject.parseFrom(value.getSegment());
                    Segment.SegmentBuilder segmentBuilder = Segment.builder().segmentId(traceSegmentObject.getTraceSegmentId());
                    logger.debug("Receive segment: Application[{}], TraceSegmentId[{}]",
                            traceSegmentObject.getApplicationId(),
                            traceSegmentObject.getTraceSegmentId());

                    for (SpanObject spanObject : traceSegmentObject.getSpansList()) {
                        Span.SpanBuilder spanBuilder = Span.builder().operationName(spanObject.getOperationName()).parentSpanId(spanObject.getParentSpanId())
                                .spanId(spanObject.getSpanId()).componentId(spanObject.getComponentId()).componentName(spanObject.getComponent())
                                .spanLayer(spanObject.getSpanLayer().toString()).endTime(spanObject.getEndTime())
                                .startTime(spanObject.getStartTime()).spanType(spanObject.getSpanType().toString())
                                .peer(spanObject.getPeer()).peerId(spanObject.getPeerId()).operationId(spanObject.getOperationNameId());

                        for (LogMessage logMessage : spanObject.getLogsList()) {
                            spanBuilder.logEventV1(logMessage.getDataList());
                        }

                        for (KeyWithStringValue tags : spanObject.getTagsList()) {
                            spanBuilder.tags(tags.getKey(), tags.getValue());
                        }

                        for (TraceSegmentReference ref : spanObject.getRefsList()) {
                            spanBuilder.ref(new Span.SegmentRef(ref));
                        }

                        segmentBuilder.addSpan(spanBuilder);
                    }

                    ValidateData.INSTANCE.getSegmentItem().addSegmentItem(traceSegmentObject.getApplicationId(), segmentBuilder.build());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
            }

            @Override public void onError(Throwable t) {

            }

            @Override public void onCompleted() {
                responseObserver.onNext(Downstream.getDefaultInstance());
                responseObserver.onCompleted();
            }
        };
    }
}
