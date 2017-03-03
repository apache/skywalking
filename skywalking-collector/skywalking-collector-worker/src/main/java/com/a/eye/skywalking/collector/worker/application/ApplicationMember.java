package com.a.eye.skywalking.collector.worker.application;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractMember;
import com.a.eye.skywalking.collector.actor.AbstractMemberProvider;
import com.a.eye.skywalking.collector.actor.MemberSystem;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.application.metric.TraceSegmentRecordMember;
import com.a.eye.skywalking.collector.worker.application.persistence.DAGNodePersistence;
import com.a.eye.skywalking.collector.worker.application.persistence.NodeInstancePersistence;
import com.a.eye.skywalking.collector.worker.application.persistence.ResponseCostPersistence;
import com.a.eye.skywalking.collector.worker.application.persistence.ResponseSummaryPersistence;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.tag.Tags;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ApplicationMember extends AbstractMember {

    private Logger logger = LogManager.getFormatterLogger(ApplicationMember.class);

    public ApplicationMember(MemberSystem memberSystem, ActorRef actorRef) {
        super(memberSystem, actorRef);
    }

    @Override
    public void preStart() throws Exception {
        logger.info("create members");
        TraceSegmentRecordMember.Factory factory = new TraceSegmentRecordMember.Factory();
        factory.createWorker(memberContext(), getSelf());
    }

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof TraceSegment) {
            logger.debug("begin translate TraceSegment Object to JsonObject");
            TraceSegment traceSegment = (TraceSegment) message;
            AbstractMember discoverMember = memberContext().memberFor(TraceSegmentRecordMember.class.getSimpleName());
            discoverMember.receive(traceSegment);

            sendToDAGNodePersistence(traceSegment);
            sendToNodeInstancePersistence(traceSegment);
            sendToResponseCostPersistence(traceSegment);
            sendToResponseSummaryPersistence(traceSegment);
        }
    }

    public static class Factory extends AbstractMemberProvider {
        @Override
        public Class memberClass() {
            return ApplicationMember.class;
        }
    }

    private void sendToDAGNodePersistence(TraceSegment traceSegment) throws Throwable {
        String code = traceSegment.getApplicationCode();

        String component = null;
        String layer = null;
        for (Span span : traceSegment.getSpans()) {
            if (span.getParentSpanId() == -1) {
                component = Tags.COMPONENT.get(span);
                layer = Tags.SPAN_LAYER.get(span);
            }
        }

        DAGNodePersistence.Metric node = new DAGNodePersistence.Metric(code, component, layer);
        tell(new NodeInstancePersistence.Factory(), RollingSelector.INSTANCE, node);
    }

    private void sendToNodeInstancePersistence(TraceSegment traceSegment) throws Throwable {
        if (traceSegment.getPrimaryRef() != null) {
            String code = traceSegment.getPrimaryRef().getApplicationCode();
            String address = traceSegment.getPrimaryRef().getPeerHost();

            NodeInstancePersistence.Metric property = new NodeInstancePersistence.Metric(code, address);
            tell(new NodeInstancePersistence.Factory(), RollingSelector.INSTANCE, property);
        }
    }

    private void sendToResponseCostPersistence(TraceSegment traceSegment) throws Throwable {
        String code = traceSegment.getApplicationCode();
        long startTime = -1;
        long endTime = -1;
        boolean isError = false;

        for (Span span : traceSegment.getSpans()) {
            if (span.getParentSpanId() == -1) {
                startTime = span.getStartTime();
                endTime = span.getEndTime();
                isError = Tags.ERROR.get(span);
            }
        }

        ResponseCostPersistence.Metric cost = new ResponseCostPersistence.Metric(code, isError, startTime, endTime);
        tell(new ResponseCostPersistence.Factory(), RollingSelector.INSTANCE, cost);
    }

    private void sendToResponseSummaryPersistence(TraceSegment traceSegment) throws Throwable {
        String code = traceSegment.getApplicationCode();
        boolean isError = false;

        for (Span span : traceSegment.getSpans()) {
            if (span.getParentSpanId() == -1) {
                isError = Tags.ERROR.get(span);
            }
        }

        ResponseSummaryPersistence.Metric summary = new ResponseSummaryPersistence.Metric(code, isError);
        tell(new ResponseSummaryPersistence.Factory(), RollingSelector.INSTANCE, summary);
    }
}
