package com.a.eye.skywalking.collector.worker.applicationref;

import akka.actor.ActorRef;
import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.actor.AbstractSyncMember;
import com.a.eye.skywalking.collector.actor.AbstractSyncMemberProvider;
import com.a.eye.skywalking.collector.worker.applicationref.analysis.DAGNodeRefAnalysis;
import com.a.eye.skywalking.collector.worker.receiver.TraceSegmentReceiver;
import com.a.eye.skywalking.trace.TraceSegmentRef;

/**
 * @author pengys5
 */
public class ApplicationRefMain extends AbstractSyncMember {

    private DAGNodeRefAnalysis dagNodeRefAnalysis;

    public ApplicationRefMain(ActorRef actorRef) throws Throwable {
        super(actorRef);
        dagNodeRefAnalysis = DAGNodeRefAnalysis.Factory.INSTANCE.createWorker(actorRef);
    }

    @Override
    public void receive(Object message) throws Exception {
        TraceSegmentReceiver.TraceSegmentTimeSlice traceSegment = (TraceSegmentReceiver.TraceSegmentTimeSlice) message;

        TraceSegmentRef traceSegmentRef = traceSegment.getTraceSegment().getPrimaryRef();
        if (traceSegmentRef != null && !StringUtil.isEmpty(traceSegmentRef.getApplicationCode())) {
            String front = traceSegmentRef.getApplicationCode();
            String behind = traceSegment.getTraceSegment().getApplicationCode();

            DAGNodeRefAnalysis.Metric nodeRef = new DAGNodeRefAnalysis.Metric(traceSegment.getMinute(), traceSegment.getSecond(), front, behind);
            dagNodeRefAnalysis.beTold(nodeRef);
        }
    }

    public static class Factory extends AbstractSyncMemberProvider<ApplicationRefMain> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class memberClass() {
            return ApplicationRefMain.class;
        }
    }
}
