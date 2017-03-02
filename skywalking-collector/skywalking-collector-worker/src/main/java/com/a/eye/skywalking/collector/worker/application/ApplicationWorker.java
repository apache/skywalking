package com.a.eye.skywalking.collector.worker.application;

import com.a.eye.skywalking.collector.actor.AbstractMember;
import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.worker.application.member.ApplicationDiscoverFactory;
import com.a.eye.skywalking.collector.worker.application.member.ApplicationDiscoverMember;
import com.a.eye.skywalking.trace.TraceSegment;

/**
 * @author pengys5
 */
public class ApplicationWorker extends AbstractWorker {

    @Override
    public void preStart() throws Exception {
        ApplicationDiscoverFactory factory = new ApplicationDiscoverFactory();
        factory.createWorker(getMemberContext(), getSelf());

        super.preStart();
    }

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof TraceSegment) {
            TraceSegment traceSegment = (TraceSegment) message;
            AbstractMember discoverMember = getMemberContext().memberFor(ApplicationDiscoverMember.class.getSimpleName());
            discoverMember.receive(traceSegment);
        }
    }
}
