package org.skywalking.apm.collector.worker.instance.heartbeat;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.RecordAnalysisMember;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.segment.entity.Segment;

public class HeartBeatAnalysis extends RecordAnalysisMember {
    private Logger logger = LogManager.getFormatterLogger(HeartBeatAnalysis.class);

    public HeartBeatAnalysis(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof Segment) {
            Segment segment = (Segment)message;
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("ii", segment.getInstanceId());
            jsonObject.addProperty("st", segment.getStartTime());
            set(segment.getInstanceId(), jsonObject);
        }
    }

    @Override protected WorkerRefs aggWorkRefs() {
        try {
            return getClusterContext().lookup(HeartBeatDataSave.Role.INSTANCE);
        } catch (WorkerNotFoundException e) {
            logger.error("The role of %s worker not found", HeartBeatDataSave.Role.INSTANCE.roleName());
        }
        return null;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<HeartBeatAnalysis> {
        @Override
        public HeartBeatAnalysis.Role role() {
            return HeartBeatAnalysis.Role.INSTANCE;
        }

        @Override
        public HeartBeatAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new HeartBeatAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Node.HeartBeatAnalysis.SIZE;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return HeartBeatAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
