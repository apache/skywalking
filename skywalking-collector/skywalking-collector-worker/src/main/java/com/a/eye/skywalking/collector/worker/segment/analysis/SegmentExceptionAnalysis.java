package com.a.eye.skywalking.collector.worker.segment.analysis;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.segment.SegmentExceptionIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.segment.entity.LogData;
import com.a.eye.skywalking.collector.worker.segment.entity.Segment;
import com.a.eye.skywalking.collector.worker.segment.entity.Span;
import com.a.eye.skywalking.collector.worker.segment.entity.tag.Tags;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentExceptionSave;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * @author pengys5
 */
public class SegmentExceptionAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(SegmentExceptionAnalysis.class);

    SegmentExceptionAnalysis(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SegmentExceptionSave.Role.INSTANCE).create(this);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof SegmentPost.SegmentWithTimeSlice) {
            SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = (SegmentPost.SegmentWithTimeSlice) message;
            Segment segment = segmentWithTimeSlice.getSegment();

            if (CollectionTools.isNotEmpty(segment.getSpans())) {
                for (Span span : segment.getSpans()) {
                    boolean isError = Tags.ERROR.get(span);

                    JsonObject dataJsonObj = new JsonObject();
                    dataJsonObj.addProperty(SegmentExceptionIndex.IS_ERROR, isError);
                    dataJsonObj.addProperty(SegmentExceptionIndex.SEG_ID, segment.getTraceSegmentId());

                    JsonArray errorKind = new JsonArray();
                    if (isError) {
                        List<LogData> logDataList = span.getLogs();
                        for (LogData logData : logDataList) {
                            if (logData.getFields().containsKey("error.kind")) {
                                errorKind.add(String.valueOf(logData.getFields().get("error.kind")));
                            }
                        }
                    }
                    dataJsonObj.add(SegmentExceptionIndex.ERROR_KIND, errorKind);
                    set(segment.getTraceSegmentId(), dataJsonObj);
                }
            }
        } else {
            logger.error("unhandled message, message instance must SegmentPost.SegmentWithTimeSlice, but is %s", message.getClass().toString());
        }
    }

    @Override
    protected WorkerRefs aggWorkRefs() {
        try {
            return getSelfContext().lookup(SegmentExceptionSave.Role.INSTANCE);
        } catch (WorkerNotFoundException e) {
            logger.error("The role of %s worker not found", SegmentExceptionSave.Role.INSTANCE.roleName());
        }
        return null;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<SegmentExceptionAnalysis> {
        @Override
        public Role role() {
            return SegmentExceptionAnalysis.Role.INSTANCE;
        }

        @Override
        public SegmentExceptionAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentExceptionAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Segment.SegmentExceptionAnalysis.SIZE;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentExceptionAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
