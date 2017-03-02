package com.a.eye.skywalking.collector.worker.applicationref;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractMember;
import com.a.eye.skywalking.collector.actor.AbstractMemberProvider;
import com.a.eye.skywalking.collector.actor.MemberSystem;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.applicationref.presistence.DAGNodeRefPersistence;
import com.a.eye.skywalking.trace.TraceSegment;

/**
 * @author pengys5
 */
public class ApplicationRefMember extends AbstractMember {

    public ApplicationRefMember(MemberSystem memberSystem, ActorRef actorRef) {
        super(memberSystem, actorRef);
    }

    @Override
    public void preStart() throws Throwable {

    }

    @Override
    public void receive(Object message) throws Throwable {
        TraceSegment traceSegment = (TraceSegment) message;

        if (traceSegment.getPrimaryRef() != null) {
            String front = traceSegment.getPrimaryRef().getApplicationCode();
            String behind = traceSegment.getApplicationCode();

            DAGNodeRefPersistence.Metric nodeRef = new DAGNodeRefPersistence.Metric(front, behind);
            tell(new DAGNodeRefPersistence.Factory(), RollingSelector.INSTANCE, nodeRef);
        }
    }

    public static class Factory extends AbstractMemberProvider {
        @Override
        public Class memberClass() {
            return ApplicationRefMember.class;
        }
    }
}
