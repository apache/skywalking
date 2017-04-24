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
import com.a.eye.skywalking.collector.worker.node.analysis.NodeCompAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeMappingDayAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeMappingHourAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeMappingMinuteAnalysis;
import com.a.eye.skywalking.collector.worker.noderef.analysis.NodeRefDayAnalysis;
import com.a.eye.skywalking.collector.worker.noderef.analysis.NodeRefHourAnalysis;
import com.a.eye.skywalking.collector.worker.noderef.analysis.NodeRefMinuteAnalysis;
import com.a.eye.skywalking.collector.worker.segment.analysis.SegmentAnalysis;
import com.a.eye.skywalking.collector.worker.segment.analysis.SegmentCostAnalysis;
import com.a.eye.skywalking.collector.worker.segment.analysis.SegmentExceptionAnalysis;
import com.a.eye.skywalking.collector.worker.segment.entity.Segment;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class SegmentPost extends AbstractPost {
    private static final Logger logger = LogManager.getFormatterLogger(SegmentPost.class);

    public SegmentPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(GlobalTraceAnalysis.Role.INSTANCE).create(this);

        getClusterContext().findProvider(SegmentAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(SegmentCostAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(SegmentExceptionAnalysis.Role.INSTANCE).create(this);

        getClusterContext().findProvider(NodeRefMinuteAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeRefHourAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeRefDayAnalysis.Role.INSTANCE).create(this);

        getClusterContext().findProvider(NodeCompAnalysis.Role.INSTANCE).create(this);

        getClusterContext().findProvider(NodeMappingDayAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeMappingHourAnalysis.Role.INSTANCE).create(this);
        getClusterContext().findProvider(NodeMappingMinuteAnalysis.Role.INSTANCE).create(this);
    }

    @Override
    protected void onReceive(Object message) throws Exception {
        if (message instanceof Segment) {
            Segment segment = (Segment) message;
            try {
                validateData(segment);
            } catch (IllegalArgumentException e) {
                return;
            }

            logger.debug("receive message instanceof TraceSegment, traceSegmentId is %s", segment.getTraceSegmentId());

            long minuteSlice = DateTools.getMinuteSlice(segment.getStartTime());
            long hourSlice = DateTools.getHourSlice(segment.getStartTime());
            long daySlice = DateTools.getDaySlice(segment.getStartTime());
            int second = DateTools.getSecond(segment.getStartTime());
            logger.debug("minuteSlice: %s, hourSlice: %s, daySlice: %s, second:%s", minuteSlice, hourSlice, daySlice, second);

            SegmentWithTimeSlice segmentWithTimeSlice = new SegmentWithTimeSlice(segment, minuteSlice, hourSlice, daySlice, second);
            getSelfContext().lookup(SegmentAnalysis.Role.INSTANCE).tell(segment);

            getSelfContext().lookup(SegmentCostAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
            getSelfContext().lookup(GlobalTraceAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
            getSelfContext().lookup(SegmentExceptionAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);

            getSelfContext().lookup(NodeCompAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);

            tellNodeRef(segmentWithTimeSlice);
            tellNodeMapping(segmentWithTimeSlice);
        }
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

    private void validateData(Segment segment) {
        if (StringUtil.isEmpty(segment.getTraceSegmentId())) {
            throw new IllegalArgumentException("traceSegmentId required");
        }
        if (0 == segment.getStartTime()) {
            throw new IllegalArgumentException("startTime required");
        }
    }

    public static class Factory extends AbstractPostProvider<SegmentPost> {
        @Override
        public String servletPath() {
            return "/segments";
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Segment.SegmentPost.SIZE;
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
        private final Segment segment;

        public SegmentWithTimeSlice(Segment segment, long minute, long hour, long day, int second) {
            super(minute, hour, day, second);
            this.segment = segment;
        }

        public Segment getSegment() {
            return segment;
        }
    }
}
