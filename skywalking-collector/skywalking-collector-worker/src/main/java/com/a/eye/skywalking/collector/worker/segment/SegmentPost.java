package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractPost;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractPostProvider;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeDayAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeHourAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeMinuteAnalysis;
import com.a.eye.skywalking.collector.worker.noderef.analysis.NodeRefDayAnalysis;
import com.a.eye.skywalking.collector.worker.noderef.analysis.NodeRefHourAnalysis;
import com.a.eye.skywalking.collector.worker.noderef.analysis.NodeRefMinuteAnalysis;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentSave;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.a.eye.skywalking.trace.TraceSegment;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class SegmentPost extends AbstractPost {

    private Logger logger = LogManager.getFormatterLogger(SegmentPost.class);

    private Gson gson = new Gson();

    public SegmentPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SegmentSave.Role.INSTANCE).create(this);

        getClusterContext().findProvider(NodeRefMinuteAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeRefHourAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeRefDayAnalysis.Role.INSTANCE).create(this);

        getClusterContext().findProvider(NodeMinuteAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeHourAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeDayAnalysis.Role.INSTANCE).create(this);
    }

    @Override
    protected void onReceive(String reqJsonStr) throws Exception {
        TraceSegment newSegment = gson.fromJson(reqJsonStr, TraceSegment.class);
        validateData(newSegment);
        logger.debug("receive message instanceof TraceSegment, traceSegmentId is %s", newSegment.getTraceSegmentId());

        long minuteSlice = DateTools.getMinuteSlice(newSegment.getStartTime());
        long hourSlice = DateTools.getHourSlice(newSegment.getStartTime());
        long daySlice = DateTools.getDaySlice(newSegment.getStartTime());
        int second = DateTools.getSecond(newSegment.getStartTime());

        SegmentWithTimeSlice segmentWithTimeSlice = new SegmentWithTimeSlice(newSegment, minuteSlice, hourSlice, daySlice, second);
        tellSegmentSave(reqJsonStr, daySlice, hourSlice, minuteSlice);

        tellNodeRef(segmentWithTimeSlice);
        tellNode(segmentWithTimeSlice);
    }

    private void tellSegmentSave(String reqJsonStr, long day, long hour, long minute) throws Exception {
        JsonObject newSegmentJson = gson.fromJson(reqJsonStr, JsonObject.class);
        newSegmentJson.addProperty("minute", minute);
        newSegmentJson.addProperty("hour", hour);
        newSegmentJson.addProperty("day", day);
        getSelfContext().lookup(SegmentSave.Role.INSTANCE).tell(newSegmentJson);
    }

    private void tellNodeRef(SegmentWithTimeSlice segmentWithTimeSlice) throws Exception {
        getSelfContext().lookup(NodeRefMinuteAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
        getSelfContext().lookup(NodeRefHourAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
        getSelfContext().lookup(NodeRefDayAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
    }

    private void tellNode(SegmentWithTimeSlice segmentWithTimeSlice) throws Exception {
        getSelfContext().lookup(NodeMinuteAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
        getSelfContext().lookup(NodeHourAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
        getSelfContext().lookup(NodeDayAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
    }

    private void validateData(TraceSegment newSegment) {
        if (StringUtil.isEmpty(newSegment.getTraceSegmentId())) {
            throw new IllegalArgumentException("traceSegmentId required");
        }
        if (0 == newSegment.getStartTime()) {
            throw new IllegalArgumentException("startTime required");
        }
    }

    public static class Factory extends AbstractPostProvider<SegmentPost> {
        public static Factory INSTANCE = new Factory();

        @Override
        public String servletPath() {
            return "/segment";
        }

        @Override
        public int queueSize() {
            return 128;
        }

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SegmentPost workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentPost(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentPost.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }

    public static class SegmentWithTimeSlice extends AbstractTimeSlice {
        private final TraceSegment traceSegment;

        public SegmentWithTimeSlice(TraceSegment traceSegment, long minute, long hour, long day, int second) {
            super(minute, hour, day, second);
            this.traceSegment = traceSegment;
        }

        public TraceSegment getTraceSegment() {
            return traceSegment;
        }
    }
}
