package com.a.eye.skywalking.collector.worker.application;

import akka.actor.ActorRef;
import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.actor.AbstractSyncMember;
import com.a.eye.skywalking.collector.actor.AbstractSyncMemberProvider;
import com.a.eye.skywalking.collector.worker.application.analysis.DAGNodeAnalysis;
import com.a.eye.skywalking.collector.worker.application.analysis.NodeInstanceAnalysis;
import com.a.eye.skywalking.collector.worker.application.analysis.ResponseCostAnalysis;
import com.a.eye.skywalking.collector.worker.application.analysis.ResponseSummaryAnalysis;
import com.a.eye.skywalking.collector.worker.application.persistence.TraceSegmentRecordPersistence;
import com.a.eye.skywalking.collector.worker.receiver.TraceSegmentReceiver;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import com.a.eye.skywalking.trace.tag.Tags;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ApplicationMain extends AbstractSyncMember {

    private Logger logger = LogManager.getFormatterLogger(ApplicationMain.class);

    private DAGNodeAnalysis dagNodeAnalysis;
    private NodeInstanceAnalysis nodeInstanceAnalysis;
    private ResponseCostAnalysis responseCostAnalysis;
    private ResponseSummaryAnalysis responseSummaryAnalysis;
    private TraceSegmentRecordPersistence recordPersistence;

    public ApplicationMain(ActorRef actorRef) throws Exception {
        super(actorRef);
        dagNodeAnalysis = DAGNodeAnalysis.Factory.INSTANCE.createWorker(actorRef);
        nodeInstanceAnalysis = NodeInstanceAnalysis.Factory.INSTANCE.createWorker(actorRef);
        responseCostAnalysis = ResponseCostAnalysis.Factory.INSTANCE.createWorker(actorRef);
        responseSummaryAnalysis = ResponseSummaryAnalysis.Factory.INSTANCE.createWorker(actorRef);
        recordPersistence = TraceSegmentRecordPersistence.Factory.INSTANCE.createWorker(actorRef);
    }

    @Override
    public void receive(Object message) throws Exception {
        if (message instanceof TraceSegmentReceiver.TraceSegmentTimeSlice) {
            logger.debug("begin translate TraceSegment Object to JsonObject");
            TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment = (TraceSegmentReceiver.TraceSegmentTimeSlice) message;

            recordPersistence.beTold(traceSegment);

            sendToDAGNodePersistence(traceSegment);
            sendToNodeInstanceAnalysis(traceSegment);
            sendToResponseCostPersistence(traceSegment);
            sendToResponseSummaryPersistence(traceSegment);
        }
    }

    public static class Factory extends AbstractSyncMemberProvider<ApplicationMain> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class memberClass() {
            return ApplicationMain.class;
        }
    }

    private void sendToDAGNodePersistence(TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment) throws Exception {
        String code = traceSegment.getTraceSegment().getApplicationCode();

        String component = null;
        String layer = null;
        for (Span span : traceSegment.getTraceSegment().getSpans()) {
            if (span.getParentSpanId() == -1) {
                component = Tags.COMPONENT.get(span);
                layer = Tags.SPAN_LAYER.get(span);
            }
        }

        DAGNodeAnalysis.Metric node = new DAGNodeAnalysis.Metric(traceSegment.getMinute(), traceSegment.getSecond(), code, component, layer);
        dagNodeAnalysis.beTold(node);
    }

    private void sendToNodeInstanceAnalysis(TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment) throws Exception {
        TraceSegmentRef traceSegmentRef = traceSegment.getTraceSegment().getPrimaryRef();

        if (traceSegmentRef != null && !StringUtil.isEmpty(traceSegmentRef.getApplicationCode())) {
            String code = traceSegmentRef.getApplicationCode();
            String address = traceSegmentRef.getPeerHost();

            NodeInstanceAnalysis.Metric property = new NodeInstanceAnalysis.Metric(traceSegment.getMinute(), traceSegment.getSecond(), code, address);
            nodeInstanceAnalysis.beTold(property);
        }
    }

    private void sendToResponseCostPersistence(TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment) throws Exception {
        String code = traceSegment.getTraceSegment().getApplicationCode();

        long startTime = -1;
        long endTime = -1;
        Boolean isError = false;

        for (Span span : traceSegment.getTraceSegment().getSpans()) {
            if (span.getParentSpanId() == -1) {
                startTime = span.getStartTime();
                endTime = span.getEndTime();
                isError = Tags.ERROR.get(span);
            }
        }

        ResponseCostAnalysis.Metric cost = new ResponseCostAnalysis.Metric(traceSegment.getMinute(), traceSegment.getSecond(), code, isError, startTime, endTime);
        responseCostAnalysis.beTold(cost);
    }

    private void sendToResponseSummaryPersistence(TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment) throws Exception {
        String code = traceSegment.getTraceSegment().getApplicationCode();
        boolean isError = false;

        for (Span span : traceSegment.getTraceSegment().getSpans()) {
            if (span.getParentSpanId() == -1) {
                isError = Tags.ERROR.get(span);
            }
        }

        ResponseSummaryAnalysis.Metric summary = new ResponseSummaryAnalysis.Metric(traceSegment.getMinute(), traceSegment.getSecond(), code, isError);
        responseSummaryAnalysis.beTold(summary);
    }
}
