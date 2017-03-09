package com.a.eye.skywalking.collector.worker.applicationref;

import akka.actor.ActorRef;
import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.actor.AbstractSyncMember;
import com.a.eye.skywalking.collector.actor.AbstractSyncMemberProvider;
import com.a.eye.skywalking.collector.worker.applicationref.analysis.DAGNodeRefAnalysis;
import com.a.eye.skywalking.trace.TraceSegment;

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
        TraceSegment traceSegment = (TraceSegment) message;

        if (traceSegment.getPrimaryRef() != null && !StringUtil.isEmpty(traceSegment.getPrimaryRef().getApplicationCode())) {
            String front = traceSegment.getPrimaryRef().getApplicationCode();
            String behind = traceSegment.getApplicationCode();

            DAGNodeRefAnalysis.Metric nodeRef = new DAGNodeRefAnalysis.Metric(front, behind);
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
