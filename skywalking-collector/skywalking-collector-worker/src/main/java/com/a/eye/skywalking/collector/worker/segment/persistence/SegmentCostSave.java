package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordPersistenceMember;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.segment.SegmentCostIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class SegmentCostSave extends RecordPersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(SegmentCostSave.class);

    @Override
    public String esIndex() {
        return SegmentCostIndex.INDEX;
    }

    @Override
    public String esType() {
        return SegmentCostIndex.TYPE_RECORD;
    }

    protected SegmentCostSave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof SegmentPost.SegmentWithTimeSlice) {
            SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = (SegmentPost.SegmentWithTimeSlice)message;
            TraceSegment segment = segmentWithTimeSlice.getTraceSegment();

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

                        RecordData recordData = new RecordData(segment.getTraceSegmentId());
                        recordData.setRecord(dataJsonObj);
                        super.analyse(recordData);
                    }
                }
            }
        } else {
            logger.error("unhandled message, message instance must JsonObject, but is %s", message.getClass().toString());
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<SegmentCostSave> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Segment.SegmentCostSave.SIZE;
        }

        @Override
        public SegmentCostSave workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentCostSave(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentCostSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
