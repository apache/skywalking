package com.a.eye.skywalking.collector.worker.receiver;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.ApplicationMain;
import com.a.eye.skywalking.collector.worker.applicationref.ApplicationRefMain;
import com.a.eye.skywalking.trace.TraceSegment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class TraceSegmentReceiver extends AbstractWorker {

    private Logger logger = LogManager.getFormatterLogger(TraceSegmentReceiver.class);

    private ApplicationMain applicationMain;

    private ApplicationRefMain applicationRefMain;


    public TraceSegmentReceiver() throws Exception {
        applicationMain = ApplicationMain.Factory.INSTANCE.createWorker(getSelf());
        applicationRefMain = ApplicationRefMain.Factory.INSTANCE.createWorker(getSelf());
    }

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof TraceSegment) {
            TraceSegment traceSegment = (TraceSegment) message;
            logger.debug("receive message instanceof TraceSegment, traceSegmentId is %s", traceSegment.getTraceSegmentId());

            applicationMain.beTold(traceSegment);
            applicationRefMain.beTold(traceSegment);
        }
    }

    public static class Factory extends AbstractWorkerProvider {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class workerClass() {
            return TraceSegmentReceiver.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.TraceSegmentReceiver.Num;
        }
    }
}
