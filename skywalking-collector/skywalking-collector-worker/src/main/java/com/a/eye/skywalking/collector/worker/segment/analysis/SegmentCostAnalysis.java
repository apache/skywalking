package com.a.eye.skywalking.collector.worker.segment.analysis;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.segment.SegmentCostIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.segment.entity.Segment;
import com.a.eye.skywalking.collector.worker.segment.entity.Span;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentCostSave;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class SegmentCostAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(SegmentCostAnalysis.class);

    SegmentCostAnalysis(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SegmentCostSave.Role.INSTANCE).create(this);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof SegmentPost.SegmentWithTimeSlice) {
            SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = (SegmentPost.SegmentWithTimeSlice) message;
            Segment segment = segmentWithTimeSlice.getSegment();

            if (CollectionTools.isNotEmpty(segment.getSpans())) {
                for (Span span : segment.getSpans()) {
                    if (span.getParentSpanId() == -1) {
                        JsonObject dataJsonObj = new JsonObject();
                        dataJsonObj.addProperty(SegmentCostIndex.SEG_ID, segment.getTraceSegmentId());
                        dataJsonObj.addProperty(SegmentCostIndex.START_TIME, span.getStartTime());
                        dataJsonObj.addProperty(SegmentCostIndex.END_TIME, span.getEndTime());
                        dataJsonObj.addProperty(SegmentCostIndex.OPERATION_NAME, span.getOperationName());
                        dataJsonObj.addProperty(SegmentCostIndex.TIME_SLICE, segmentWithTimeSlice.getMinute());

                        long startTime = span.getStartTime();
                        long endTime = span.getEndTime();
                        long cost = endTime - startTime;
                        if (cost == 0) {
                            cost = 1;
                        }
                        dataJsonObj.addProperty(SegmentCostIndex.COST, cost);
                        setRecord(segment.getTraceSegmentId(), dataJsonObj);
                    }
                }
            }
        } else {
            logger.error("unhandled message, message instance must SegmentPost.SegmentWithTimeSlice, but is %s", message.getClass().toString());
        }
    }

    @Override
    protected WorkerRefs aggWorkRefs() {
        try {
            return getSelfContext().lookup(SegmentCostSave.Role.INSTANCE);
        } catch (WorkerNotFoundException e) {
            logger.error("The role of %s worker not found", SegmentCostSave.Role.INSTANCE.roleName());
        }
        return null;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<SegmentCostAnalysis> {
        @Override
        public Role role() {
            return SegmentCostAnalysis.Role.INSTANCE;
        }

        @Override
        public SegmentCostAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentCostAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Segment.SegmentCostAnalysis.SIZE;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentCostAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
