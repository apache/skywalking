package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.globaltrace.analysis.GlobalTraceAnalysis;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractPost;
import com.a.eye.skywalking.collector.worker.httpserver.AbstractPostProvider;
import com.a.eye.skywalking.collector.worker.node.analysis.*;
import com.a.eye.skywalking.collector.worker.noderef.analysis.NodeRefDayAnalysis;
import com.a.eye.skywalking.collector.worker.noderef.analysis.NodeRefHourAnalysis;
import com.a.eye.skywalking.collector.worker.noderef.analysis.NodeRefMinuteAnalysis;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentCostSave;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentExceptionSave;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentSave;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.a.eye.skywalking.trace.SegmentsMessage;
import com.a.eye.skywalking.trace.TraceSegment;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * @author pengys5
 */
public class SegmentPost extends AbstractPost {

    private Logger logger = LogManager.getFormatterLogger(SegmentPost.class);

    private Gson gson;

    public SegmentPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
        gson = new Gson();
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(GlobalTraceAnalysis.Role.INSTANCE).create(this);

        getClusterContext().findProvider(NodeCompAnalysis.Role.INSTANCE).create(this);

        getClusterContext().findProvider(SegmentSave.Role.INSTANCE).create(this);
        getClusterContext().findProvider(SegmentCostSave.Role.INSTANCE).create(this);
        getClusterContext().findProvider(SegmentExceptionSave.Role.INSTANCE).create(this);

        getClusterContext().findProvider(NodeRefMinuteAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeRefHourAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeRefDayAnalysis.Role.INSTANCE).create(this);

        getClusterContext().findProvider(NodeMappingDayAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeMappingHourAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeMappingMinuteAnalysis.Role.INSTANCE).create(this);
    }

    @Override
    protected void onReceive(String reqJsonStr) throws Exception {
        SegmentsMessage segmentsMessage = gson.fromJson(reqJsonStr, SegmentsMessage.class);
        List<TraceSegment> segmentList = segmentsMessage.getSegments();
        for (TraceSegment newSegment : segmentList) {
            try {
                validateData(newSegment);
            } catch (IllegalArgumentException e) {
                continue;
            }

            logger.debug("receive message instanceof TraceSegment, traceSegmentId is %s", newSegment.getTraceSegmentId());

            long minuteSlice = DateTools.getMinuteSlice(newSegment.getStartTime());
            long hourSlice = DateTools.getHourSlice(newSegment.getStartTime());
            long daySlice = DateTools.getDaySlice(newSegment.getStartTime());
            int second = DateTools.getSecond(newSegment.getStartTime());
            logger.debug("minuteSlice: %s, hourSlice: %s, daySlice: %s, second:%s", minuteSlice, hourSlice, daySlice, second);

            SegmentWithTimeSlice segmentWithTimeSlice = new SegmentWithTimeSlice(newSegment, minuteSlice, hourSlice, daySlice, second);
            String newSegmentJsonStr = gson.toJson(newSegment);
            tellSegmentSave(newSegmentJsonStr, daySlice, hourSlice, minuteSlice);

            getSelfContext().lookup(SegmentCostSave.Role.INSTANCE).tell(segmentWithTimeSlice);
            getSelfContext().lookup(GlobalTraceAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
            getSelfContext().lookup(SegmentExceptionSave.Role.INSTANCE).tell(segmentWithTimeSlice);

            getSelfContext().lookup(NodeCompAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);

            tellNodeRef(segmentWithTimeSlice);
            tellNodeMapping(segmentWithTimeSlice);
        }
    }

    private void tellSegmentSave(String newSegmentJsonStr, long day, long hour, long minute) throws Exception {
        JsonObject newSegmentJson = gson.fromJson(newSegmentJsonStr, JsonObject.class);
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

    private void tellNodeMapping(SegmentWithTimeSlice segmentWithTimeSlice) throws Exception {
        getSelfContext().lookup(NodeMappingMinuteAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
        getSelfContext().lookup(NodeMappingHourAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
        getSelfContext().lookup(NodeMappingDayAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
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
            return "/segments";
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Segment.SegmentPost.Size;
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
