package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordPersistenceMember;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;
import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class SegmentSave extends RecordPersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(SegmentSave.class);

    @Override
    public String esIndex() {
        return SegmentIndex.INDEX;
    }

    @Override
    public String esType() {
        return AbstractIndex.TYPE_RECORD;
    }

    public SegmentSave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof JsonObject) {
            JsonObject segmentJson = (JsonObject)message;
            RecordData recordData = new RecordData(segmentJson.get("ts").getAsString());
            recordData.setRecord(segmentJson);
            super.analyse(recordData);
        } else {
            logger.error("unhandled message, message instance must JsonObject, but is %s", message.getClass().toString());
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<SegmentSave> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Segment.SegmentSave.SIZE;
        }

        @Override
        public SegmentSave workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentSave(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
