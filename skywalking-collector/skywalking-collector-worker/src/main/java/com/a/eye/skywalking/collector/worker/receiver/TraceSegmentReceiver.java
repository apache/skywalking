package com.a.eye.skywalking.collector.worker.receiver;

import com.a.eye.skywalking.collector.actor.AbstractMember;
import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.ApplicationMember;
import com.a.eye.skywalking.collector.worker.applicationref.ApplicationRefMember;
import com.a.eye.skywalking.trace.TraceSegment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class TraceSegmentReceiver extends AbstractWorker {

    private Logger logger = LogManager.getFormatterLogger(TraceSegmentReceiver.class);

    @Override
    public void preStart() throws Exception {
        ApplicationMember.Factory factory = new ApplicationMember.Factory();
        factory.createWorker(memberContext(), getSelf());
        super.preStart();
    }

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof TraceSegment) {
            TraceSegment traceSegment = (TraceSegment) message;
            logger.debug("receive message instanceof TraceSegment, traceSegmentId is %s", traceSegment.getTraceSegmentId());

            AbstractMember applicationMember = memberContext().memberFor(ApplicationMember.class.getSimpleName());
            applicationMember.receive(traceSegment);

            AbstractMember applicationRefMember = memberContext().memberFor(ApplicationRefMember.class.getSimpleName());
            applicationRefMember.receive(traceSegment);
        }
    }

    public static class Factory extends AbstractWorkerProvider {
        @Override
        public Class workerClass() {
            return TraceSegmentReceiver.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.WorkerNum.TraceSegmentReceiver_Num;
        }
    }
}
