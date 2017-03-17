package com.a.eye.skywalking.collector.worker.application.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordPersistenceMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class NodeInstancePersistence extends RecordPersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(NodeInstancePersistence.class);

    public NodeInstancePersistence(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return "application";
    }

    @Override
    public String esType() {
        return "node_instance";
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeInstancePersistence> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeInstancePersistence workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeInstancePersistence(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Persistence.NodeInstancePersistence.Size;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeInstancePersistence.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
