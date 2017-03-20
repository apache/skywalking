package com.a.eye.skywalking.collector.worker.receiver;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.worker.application.ApplicationMain;
import com.a.eye.skywalking.collector.worker.applicationref.ApplicationRefMain;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractReceiver;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractReceiverProvider;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.a.eye.skywalking.trace.TraceSegment;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class TraceSegmentReceiver extends AbstractReceiver {

    private Logger logger = LogManager.getFormatterLogger(TraceSegmentReceiver.class);

    public TraceSegmentReceiver(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(ApplicationMain.Role.INSTANCE).create(this);
        getClusterContext().findProvider(ApplicationRefMain.Role.INSTANCE).create(this);
    }

    @Override
    protected void onReceive(JsonObject request) throws Exception {
        logger.debug("receive message instanceof TraceSegment, traceSegmentId is %s", request.get("ts"));
//        long timeSlice = DateTools.timeStampToTimeSlice(traceSegment.getStartTime());
//        int second = DateTools.timeStampToSecond(traceSegment.getStartTime());
//
//        TraceSegmentTimeSlice segmentTimeSlice = new TraceSegmentTimeSlice(timeSlice, second, traceSegment);
//        getSelfContext().lookup(ApplicationMain.Role.INSTANCE).tell(segmentTimeSlice);
//        getSelfContext().lookup(ApplicationRefMain.Role.INSTANCE).tell(segmentTimeSlice);
    }

    public static class Factory extends AbstractReceiverProvider<TraceSegmentReceiver> {
        public static Factory INSTANCE = new Factory();

        @Override
        public String servletPath() {
            return "/receiver/traceSegment";
        }

        @Override
        public int queueSize() {
            return 128;
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
