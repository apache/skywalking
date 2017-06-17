package org.skywalking.apm.collector.worker.instance.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.WorkerNotFoundException;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.RecordAnalysisMember;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.instance.RegistryPost;
import org.skywalking.apm.collector.worker.instance.persistence.InstanceSave;
import org.skywalking.apm.collector.worker.segment.persistence.SegmentCostSave;

public class InstanceAnalysis extends RecordAnalysisMember {
    private Logger logger = LogManager.getFormatterLogger(InstanceAnalysis.class);

    public InstanceAnalysis(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(InstanceSave.Role.INSTANCE).create(this);
    }

    @Override
    public void analyse(Object message) {
        if (message instanceof RegistryPost.InstanceInfo) {
            try {
                RegistryPost.InstanceInfo registryInfo = (RegistryPost.InstanceInfo)message;
                set(String.valueOf(registryInfo.getInstanceId()), registryInfo.toJsonObject());
            } catch (Exception e) {
                logger.error("Failed to save instance Info.", e);
            }
        }
    }

    @Override
    protected WorkerRefs aggWorkRefs() {
        try {
            return getSelfContext().lookup(InstanceSave.Role.INSTANCE);
        } catch (WorkerNotFoundException e) {
            logger.error("The role of %s worker not found", InstanceSave.Role.INSTANCE.roleName());
        }
        return null;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<InstanceAnalysis> {
        @Override
        public InstanceAnalysis.Role role() {
            return InstanceAnalysis.Role.INSTANCE;
        }

        @Override
        public InstanceAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new InstanceAnalysis(role(), clusterContext, new LocalWorkerContext());
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
            return InstanceAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
