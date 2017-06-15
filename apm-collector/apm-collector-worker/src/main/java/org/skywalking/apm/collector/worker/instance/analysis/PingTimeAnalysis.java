package org.skywalking.apm.collector.worker.instance.analysis;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.actor.selector.AbstractHashMessage;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.RecordAnalysisMember;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.instance.PingTimeIndex;
import org.skywalking.apm.collector.worker.instance.persistence.PingTimeUpdater;

public class PingTimeAnalysis extends RecordAnalysisMember {
    private Logger logger = LogManager.getFormatterLogger(PingTimeAnalysis.class);

    public PingTimeAnalysis(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) {
        if (message instanceof Ping) {
            Ping ping = (Ping)message;

            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty(PingTimeIndex.INSTANCE_ID, ping.getInstanceId());
            jsonObject.addProperty(PingTimeIndex.PING_TIME, ping.getPingTime());
            
            set(String.valueOf(ping.getInstanceId()), jsonObject);
        } else {
            logger.error("unhandled message, message instance must PingTimeAnalysis.Ping, but is %s", message.getClass().toString());
        }

    }

    @Override
    protected WorkerRefs aggWorkRefs() {
        try {
            return getSelfContext().lookup(PingTimeUpdater.Role.INSTANCE);
        } catch (WorkerNotFoundException e) {
            logger.error("The role of %s worker not found", PingTimeUpdater.Role.INSTANCE.roleName());
        }
        return null;
    }

    public static class Ping extends AbstractHashMessage {
        private long instanceId;
        private long pingTime;

        public Ping(long instanceId, long pingTime) {
            super(String.valueOf(instanceId));
            this.instanceId = instanceId;
            this.pingTime = pingTime;
        }

        public long getInstanceId() {
            return instanceId;
        }

        public long getPingTime() {
            return pingTime;
        }
    }

    @Override public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(PingTimeUpdater.Role.INSTANCE).create(this);
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
            return WorkerConfig.Queue.Instance.PingTimeAnalysis.SIZE;
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
            return new HashCodeSelector();
        }
    }
}
