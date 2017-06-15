package org.skywalking.apm.collector.worker.instance.analysis;

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
import org.skywalking.apm.collector.worker.instance.PingPost;
import org.skywalking.apm.collector.worker.instance.persistence.PingTimeAgg;

public class PingTimeAnalysis extends RecordAnalysisMember {
    private Logger logger = LogManager.getFormatterLogger(PingTimeAnalysis.class);

    public PingTimeAnalysis(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) {
        if (message instanceof PingPost.Ping) {
            PingPost.Ping ping = (PingPost.Ping)message;
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("pt", ping.getPingTime());
            set(String.valueOf(ping.getInstanceId()), jsonObject);
        }
    }


    @Override
    protected WorkerRefs aggWorkRefs() {
        try {
            return getClusterContext().lookup(PingTimeAgg.Role.INSTANCE);
        } catch (WorkerNotFoundException e) {
            logger.error("The role of %s worker not found", PingTimeAgg.Role.INSTANCE.roleName());
        }
        return null;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<PingTimeAnalysis> {
        @Override
        public PingTimeAnalysis.Role role() {
            return PingTimeAnalysis.Role.INSTANCE;
        }

        @Override
        public PingTimeAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new PingTimeAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Segment.SegmentAnalysis.SIZE;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return PingTimeAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
