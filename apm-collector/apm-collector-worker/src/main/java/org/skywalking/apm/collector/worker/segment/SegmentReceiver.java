package org.skywalking.apm.collector.worker.segment;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerInvokeException;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.globaltrace.analysis.GlobalTraceAnalysis;
import org.skywalking.apm.collector.worker.grpcserver.AbstractReceiver;
import org.skywalking.apm.collector.worker.httpserver.ArgumentsParseException;
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
import org.skywalking.apm.collector.worker.segment.entity.SegmentAndBase64;
import org.skywalking.apm.collector.worker.storage.AbstractTimeSlice;
import org.skywalking.apm.collector.worker.tools.DateTools;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.UpstreamSegment;
import org.skywalking.apm.util.StringUtil;

/**
 * @author pengys5
 */
public class SegmentReceiver extends AbstractReceiver {
    private static final Logger logger = LogManager.getFormatterLogger(SegmentReceiver.class);

    public SegmentReceiver(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
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

    /**
     * Read segment's buffer from buffer reader by stream mode. when finish read one segment then send to analysis.
     * This method in there, so post servlet just can receive segments data.
     */
    @Override protected void onReceive(
        Object request) throws ArgumentsParseException, WorkerInvokeException, WorkerNotFoundException {
        if (request instanceof UpstreamSegment) {
            UpstreamSegment upstreamSegment = (UpstreamSegment)request;
            ByteString segmentByte = upstreamSegment.getSegment();
            List<String> globalTraceIds = upstreamSegment.getGlobalTraceIdsList();

            String segmentBase64 = new String(Base64.encodeBase64(segmentByte.toByteArray()));

            TraceSegmentObject segment;
            try {
                segment = TraceSegmentObject.parseFrom(segmentByte);
            } catch (InvalidProtocolBufferException e) {
                throw new ArgumentsParseException(e.getMessage(), e);
            }
            tellWorkers(new SegmentAndBase64(segment, segmentBase64), globalTraceIds);
        }
    }

    private void tellWorkers(
        SegmentAndBase64 segmentAndBase64,
        List<String> globalTraceIds) throws WorkerNotFoundException, WorkerInvokeException {
        TraceSegmentObject segment = segmentAndBase64.getObject();
        try {
            validateData(segment);
        } catch (ArgumentsParseException e) {
            logger.error(e.getMessage(), e);
            return;
        }

        logger.debug("receive message instanceof TraceSegment, traceSegmentId is %s", segment.getTraceSegmentId());
        SpanObject firstSpan = segment.getSpans(segment.getSpansCount() - 1);

        long minuteSlice = DateTools.getMinuteSlice(firstSpan.getStartTime());
        long hourSlice = DateTools.getHourSlice(firstSpan.getStartTime());
        long daySlice = DateTools.getDaySlice(firstSpan.getStartTime());
        int second = DateTools.getSecond(firstSpan.getStartTime());
        logger.debug("minuteSlice: %s, hourSlice: %s, daySlice: %s, second:%s", minuteSlice, hourSlice, daySlice, second);

        SegmentWithTimeSlice segmentWithTimeSlice = new SegmentWithTimeSlice(segment, globalTraceIds, minuteSlice, hourSlice, daySlice, second);
        getSelfContext().lookup(SegmentAnalysis.Role.INSTANCE).tell(segmentAndBase64);

        getSelfContext().lookup(SegmentCostAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
        getSelfContext().lookup(GlobalTraceAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);
        getSelfContext().lookup(SegmentExceptionAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);

        getSelfContext().lookup(NodeCompAnalysis.Role.INSTANCE).tell(segmentWithTimeSlice);

        tellNodeRef(segmentWithTimeSlice);
        tellNodeMapping(segmentWithTimeSlice);
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

    private void validateData(TraceSegmentObject segment) throws ArgumentsParseException {
        if (StringUtil.isEmpty(segment.getTraceSegmentId())) {
            throw new ArgumentsParseException("traceSegmentId required");
        }
        if (segment.getSpansCount() < 1) {
            throw new ArgumentsParseException("must contain at least one span");
        }
        SpanObject firstSpan = segment.getSpans(segment.getSpansCount() - 1);
        if (firstSpan.getSpanId() != 0 && firstSpan.getParentSpanId() != -1) {
            throw new ArgumentsParseException("first span id must equals 0 and parent span id must equals -1");
        }
        if (0 == firstSpan.getStartTime()) {
            throw new ArgumentsParseException("startTime required");
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<SegmentReceiver> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SegmentReceiver workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentReceiver(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentReceiver.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }

    public static class SegmentWithTimeSlice extends AbstractTimeSlice {
        private final TraceSegmentObject segment;

        private final List<String> globalTraceIds;

        public SegmentWithTimeSlice(TraceSegmentObject segment, List<String> globalTraceIds, long minute, long hour,
            long day, int second) {
            super(minute, hour, day, second);
            this.segment = segment;
            this.globalTraceIds = globalTraceIds;
        }

        public TraceSegmentObject getSegment() {
            return segment;
        }

        public List<String> getGlobalTraceIds() {
            return globalTraceIds;
        }
    }
}
