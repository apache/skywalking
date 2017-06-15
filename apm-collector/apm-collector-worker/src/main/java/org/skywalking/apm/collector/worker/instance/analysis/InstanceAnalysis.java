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
import org.skywalking.apm.collector.worker.instance.InstanceIndex;
import org.skywalking.apm.collector.worker.instance.persistence.InstanceSaver;

public class InstanceAnalysis extends RecordAnalysisMember {
    private Logger logger = LogManager.getFormatterLogger(InstanceAnalysis.class);

    public InstanceAnalysis(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(InstanceSaver.Role.INSTANCE).create(this);
    }

    @Override
    public void analyse(Object message) {
        if (message instanceof Instance) {
            Instance instance = (Instance)message;

            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty(InstanceIndex.APPLICATION_CODE, instance.getApplicationCode());
            jsonObject.addProperty(InstanceIndex.INSTANCE_ID, instance.getInstanceId());
            jsonObject.addProperty(InstanceIndex.REGISTRY_TIME, instance.getRegistryTime());

            set(String.valueOf(instance.getInstanceId()), jsonObject);
        } else {
            logger.error("unhandled message, message instance must InstanceAnalysis.Instance, but is %s", message.getClass().toString());
        }
    }

    public static class Instance extends AbstractHashMessage {
        private String applicationCode;
        private long instanceId;
        private long registryTime;

        public Instance(String applicationCode, long instanceId, long registryTime) {
            super(String.valueOf(instanceId));
            this.applicationCode = applicationCode;
            this.instanceId = instanceId;
            this.registryTime = registryTime;
        }

        public long getRegistryTime() {
            return registryTime;
        }

        public String getApplicationCode() {
            return applicationCode;
        }

        public long getInstanceId() {
            return instanceId;
        }
    }

    @Override
    protected WorkerRefs aggWorkRefs() {
        try {
            return getSelfContext().lookup(InstanceSaver.Role.INSTANCE);
        } catch (WorkerNotFoundException e) {
            logger.error("The role of %s worker not found", InstanceSaver.Role.INSTANCE.roleName());
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
            return WorkerConfig.Queue.Instance.InstanceAnalysis.SIZE;
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
            return new HashCodeSelector();
        }
    }
}
