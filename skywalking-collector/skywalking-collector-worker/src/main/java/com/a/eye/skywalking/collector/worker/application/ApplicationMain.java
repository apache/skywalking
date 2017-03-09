package com.a.eye.skywalking.collector.worker.application;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractSyncMember;
import com.a.eye.skywalking.collector.actor.AbstractSyncMemberProvider;
import com.a.eye.skywalking.collector.worker.application.analysis.DAGNodeAnalysis;
import com.a.eye.skywalking.collector.worker.application.analysis.NodeInstanceAnalysis;
import com.a.eye.skywalking.collector.worker.application.analysis.ResponseCostAnalysis;
import com.a.eye.skywalking.collector.worker.application.analysis.ResponseSummaryAnalysis;
import com.a.eye.skywalking.collector.worker.application.persistence.TraceSegmentRecordPersistence;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
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
        if (message instanceof TraceSegment) {
            logger.debug("begin translate TraceSegment Object to JsonObject");
            TraceSegment traceSegment = (TraceSegment) message;
            int second = DateTools.timeStampToSecond(traceSegment.getStartTime());

            recordPersistence.beTold(traceSegment);

            sendToDAGNodePersistence(traceSegment);
            sendToNodeInstanceAnalysis(traceSegment);
            sendToResponseCostPersistence(traceSegment, second);
            sendToResponseSummaryPersistence(traceSegment, second);
        }
    }

    public static class Factory extends AbstractSyncMemberProvider<ApplicationMain> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class memberClass() {
            return ApplicationMain.class;
        }
    }

    private void sendToDAGNodePersistence(TraceSegment traceSegment) throws Exception {
        String code = traceSegment.getApplicationCode();

        String component = null;
        String layer = null;
        for (Span span : traceSegment.getSpans()) {
            if (span.getParentSpanId() == -1) {
                component = Tags.COMPONENT.get(span);
                layer = Tags.SPAN_LAYER.get(span);
            }
        }

        DAGNodeAnalysis.Metric node = new DAGNodeAnalysis.Metric(code, component, layer);
        dagNodeAnalysis.beTold(node);
    }

    private void sendToNodeInstanceAnalysis(TraceSegment traceSegment) throws Exception {
        if (traceSegment.getPrimaryRef() != null) {
            String code = traceSegment.getPrimaryRef().getApplicationCode();
            String address = traceSegment.getPrimaryRef().getPeerHost();

            NodeInstanceAnalysis.Metric property = new NodeInstanceAnalysis.Metric(code, address);
            nodeInstanceAnalysis.beTold(property);
        }
    }

    private void sendToResponseCostPersistence(TraceSegment traceSegment, int second) throws Exception {
        String code = traceSegment.getApplicationCode();

        long startTime = -1;
        long endTime = -1;
        Boolean isError = false;

        for (Span span : traceSegment.getSpans()) {
            if (span.getParentSpanId() == -1) {
                startTime = span.getStartTime();
                endTime = span.getEndTime();
                isError = Tags.ERROR.get(span);
            }
        }

        ResponseCostAnalysis.Metric cost = new ResponseCostAnalysis.Metric(code, second, isError, startTime, endTime);
        responseCostAnalysis.beTold(cost);
    }

    private void sendToResponseSummaryPersistence(TraceSegment traceSegment, int second) throws Exception {
        String code = traceSegment.getApplicationCode();
        boolean isError = false;

        for (Span span : traceSegment.getSpans()) {
            if (span.getParentSpanId() == -1) {
                isError = Tags.ERROR.get(span);
            }
        }

        ResponseSummaryAnalysis.Metric summary = new ResponseSummaryAnalysis.Metric(code, second, isError);
        responseSummaryAnalysis.beTold(summary);
    }
}
