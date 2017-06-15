package org.skywalking.apm.collector.worker.segment;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerInvokeException;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.globaltrace.analysis.GlobalTraceAnalysis;
import org.skywalking.apm.collector.worker.httpserver.AbstractStreamPost;
import org.skywalking.apm.collector.worker.httpserver.AbstractStreamPostProvider;
import org.skywalking.apm.collector.worker.httpserver.ArgumentsParseException;
import org.skywalking.apm.collector.worker.instance.analysis.PingTimeAnalysis;
import org.skywalking.apm.collector.worker.node.analysis.NodeCompAnalysis;
import org.skywalking.apm.collector.worker.node.analysis.NodeMappingDayAnalysis;
import org.skywalking.apm.collector.worker.node.analysis.NodeMappingHourAnalysis;
import org.skywalking.apm.collector.worker.node.analysis.NodeMappingMinuteAnalysis;
import org.skywalking.apm.collector.worker.noderef.analysis.NodeRefDayAnalysis;
import org.skywalking.apm.collector.worker.noderef.analysis.NodeRefHourAnalysis;
import org.skywalking.apm.collector.worker.noderef.analysis.NodeRefMinuteAnalysis;
import org.skywalking.apm.collector.worker.segment.analysis.SegmentAnalysis;
import org.skywalking.apm.collector.worker.segment.analysis.SegmentCostAnalysis;
import org.skywalking.apm.collector.worker.segment.analysis.SegmentExceptionAnalysis;
import org.skywalking.apm.collector.worker.segment.entity.Segment;
import org.skywalking.apm.collector.worker.segment.entity.SegmentAndJson;
import org.skywalking.apm.collector.worker.segment.entity.SegmentDeserialize;
import org.skywalking.apm.collector.worker.storage.AbstractTimeSlice;
import org.skywalking.apm.collector.worker.tools.DateTools;
import org.skywalking.apm.util.StringUtil;

/**
 * @author pengys5
 */
public class SegmentPost extends AbstractStreamPost {
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

        getClusterContext().findProvider(PingTimeAnalysis.Role.INSTANCE).create(this);
    }

    /**
     * Read segment's buffer from buffer reader by stream mode. when finish read one segment then send to analysis.
     * This method in there, so post servlet just can receive segments data.
     */
    @Override protected void onReceive(BufferedReader bufferedReader,
        JsonObject response) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        Segment segment;
        try {
            do {
                int character;
                StringBuilder builder = new StringBuilder();
                while ((character = bufferedReader.read()) != ' ') {
                    if (character == -1) {
                        return;
                    }
                    builder.append((char)character);
                }

                int length = Integer.valueOf(builder.toString());
                builder = new StringBuilder();

                char[] buffer = new char[length];
                int readLength = bufferedReader.read(buffer, 0, length);
                if (readLength != length) {
                    logger.error("The actual data length was different from the length in data head! ");
                    return;
                }
                builder.append(buffer);

                String segmentJsonStr = builder.toString();
                segment = SegmentDeserialize.INSTANCE.deserializeSingle(segmentJsonStr);
                tellWorkers(new SegmentAndJson(segment, segmentJsonStr));
            }
            while (segment != null);
        } catch (IOException e) {
            throw new ArgumentsParseException(e.getMessage(), e);
        }
    }

    private void tellWorkers(SegmentAndJson segmentAndJson) throws WorkerNotFoundException, WorkerInvokeException {
        Segment segment = segmentAndJson.getSegment();
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
        getSelfContext().lookup(SegmentAnalysis.Role.INSTANCE).tell(segmentAndJson);

        getSelfContext().lookup(SegmentCostAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
        getSelfContext().lookup(GlobalTraceAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
        getSelfContext().lookup(SegmentExceptionAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);

        getSelfContext().lookup(NodeCompAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);

        tellNodeRef(segmentWithTimeSlice);
        tellNodeMapping(segmentWithTimeSlice);

        getSelfContext().lookup(PingTimeAnalysis.Role.INSTANCE).tell(new PingTimeAnalysis.Ping(segment.getInstanceId(), minuteSlice));
    }

    private void tellNodeRef(
        SegmentWithTimeSlice segmentWithTimeSlice) throws WorkerNotFoundException, WorkerInvokeException {
        getSelfContext().lookup(NodeRefMinuteAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
        getSelfContext().lookup(NodeRefHourAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
        getSelfContext().lookup(NodeRefDayAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
    }

    private void tellNodeMapping(
        SegmentWithTimeSlice segmentWithTimeSlice) throws WorkerNotFoundException, WorkerInvokeException {
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

    public static class Factory extends AbstractStreamPostProvider<SegmentPost> {
        @Override
        public String servletPath() {
            return "/segments";
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
