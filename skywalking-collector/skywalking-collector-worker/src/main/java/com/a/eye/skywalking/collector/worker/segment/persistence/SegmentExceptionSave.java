package com.a.eye.skywalking.collector.worker.segment.persistence;


import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordPersistenceMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.segment.SegmentExceptionIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.a.eye.skywalking.trace.LogData;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.tag.Tags;
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
        return SegmentExceptionIndex.Index;
    }

    @Override
    public String esType() {
        return AbstractIndex.Type_Record;
    }

    protected SegmentExceptionSave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof SegmentPost.SegmentWithTimeSlice) {
            SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = (SegmentPost.SegmentWithTimeSlice) message;
            TraceSegment segment = segmentWithTimeSlice.getTraceSegment();

            if (CollectionTools.isNotEmpty(segment.getSpans())) {
                for (Span span : segment.getSpans()) {
                    boolean isError = Tags.ERROR.get(span);

                    JsonObject dataJsonObj = new JsonObject();
                    dataJsonObj.addProperty(SegmentExceptionIndex.IsError, isError);
                    dataJsonObj.addProperty(SegmentExceptionIndex.SegId, segment.getTraceSegmentId());

                    JsonArray errorKind = new JsonArray();
                    if (isError) {
                        List<LogData> logDataList = span.getLogs();
                        for (LogData logData : logDataList) {
                            if (logData.getFields().containsKey("error.kind")) {
                                errorKind.add(String.valueOf(logData.getFields().get("error.kind")));
                            }
                        }
                    }
                    dataJsonObj.add(SegmentExceptionIndex.ErrorKind, errorKind);

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
            return WorkerConfig.Queue.Segment.SegmentExceptionSave.Size;
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
