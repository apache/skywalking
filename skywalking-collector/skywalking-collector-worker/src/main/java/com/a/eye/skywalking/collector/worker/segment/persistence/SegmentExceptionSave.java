package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordPersistenceMember;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.segment.SegmentExceptionIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.segment.entity.LogData;
import com.a.eye.skywalking.collector.worker.segment.entity.Segment;
import com.a.eye.skywalking.collector.worker.segment.entity.Span;
import com.a.eye.skywalking.collector.worker.segment.entity.tag.Tags;
import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * @author pengys5
 */
public class SegmentExceptionSave extends RecordPersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(SegmentExceptionSave.class);

    @Override
    public String esIndex() {
        return SegmentExceptionIndex.INDEX;
    }

    @Override
    public String esType() {
        return AbstractIndex.TYPE_RECORD;
    }

    protected SegmentExceptionSave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof SegmentPost.SegmentWithTimeSlice) {
            SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = (SegmentPost.SegmentWithTimeSlice)message;
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

                    RecordData recordData = new RecordData(segment.getTraceSegmentId());
                    recordData.setRecord(dataJsonObj);
                    super.analyse(recordData);
                }
            }
        } else {
            logger.error("unhandled message, message instance must JsonObject, but is %s", message.getClass().toString());
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<SegmentExceptionSave> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Segment.SegmentExceptionSave.SIZE;
        }

        @Override
        public SegmentExceptionSave workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentExceptionSave(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentExceptionSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
