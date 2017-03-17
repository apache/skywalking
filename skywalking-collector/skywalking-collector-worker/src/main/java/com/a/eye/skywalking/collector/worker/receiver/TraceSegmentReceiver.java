package com.a.eye.skywalking.collector.worker.receiver;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.role.TraceSegmentReceiverRole;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.ApplicationMain;
import com.a.eye.skywalking.collector.worker.applicationref.ApplicationRefMain;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.a.eye.skywalking.trace.TraceSegment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class TraceSegmentReceiver extends AbstractClusterWorker {

    private Logger logger = LogManager.getFormatterLogger(TraceSegmentReceiver.class);

    public TraceSegmentReceiver(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFountException {
        getClusterContext().findProvider(ApplicationMain.Role.INSTANCE).create(this);
        getClusterContext().findProvider(ApplicationRefMain.Role.INSTANCE).create(this);
    }

    @Override
    public void work(Object message) throws Exception {
        if (message instanceof TraceSegment) {
            TraceSegment traceSegment = (TraceSegment) message;
            logger.debug("receive message instanceof TraceSegment, traceSegmentId is %s", traceSegment.getTraceSegmentId());
            long timeSlice = DateTools.timeStampToTimeSlice(traceSegment.getStartTime());
            int second = DateTools.timeStampToSecond(traceSegment.getStartTime());

            TraceSegmentTimeSlice segmentTimeSlice = new TraceSegmentTimeSlice(timeSlice, second, traceSegment);
            getSelfContext().lookup(ApplicationMain.Role.INSTANCE).tell(segmentTimeSlice);
            getSelfContext().lookup(ApplicationRefMain.Role.INSTANCE).tell(segmentTimeSlice);
        }
    }

    public static class Factory extends AbstractClusterWorkerProvider<TraceSegmentReceiver> {
        public static Factory INSTANCE = new Factory();

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.TraceSegmentReceiver.Num;
        }

        @Override
        public Role role() {
            return TraceSegmentReceiverRole.INSTANCE;
        }

        @Override
        public TraceSegmentReceiver workerInstance(ClusterWorkerContext clusterContext) {
            return new TraceSegmentReceiver(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public static class TraceSegmentTimeSlice extends AbstractTimeSlice {
        private final TraceSegment traceSegment;

        public TraceSegmentTimeSlice(long timeSliceMinute, int second, TraceSegment traceSegment) {
            super(timeSliceMinute, second);
            this.traceSegment = traceSegment;
        }

        public TraceSegment getTraceSegment() {
            return traceSegment;
        }
    }
}
