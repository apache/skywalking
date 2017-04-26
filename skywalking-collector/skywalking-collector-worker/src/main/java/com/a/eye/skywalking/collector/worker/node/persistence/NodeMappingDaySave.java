package com.a.eye.skywalking.collector.worker.node.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordPersistenceMember;
import com.a.eye.skywalking.collector.worker.node.NodeMappingIndex;
import com.a.eye.skywalking.collector.worker.storage.PersistenceWorkerListener;

/**
 * @author pengys5
 */
public class NodeMappingDaySave extends RecordPersistenceMember {

    NodeMappingDaySave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
                       LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return NodeMappingIndex.INDEX;
    }

    @Override
    public String esType() {
        return NodeMappingIndex.TYPE_DAY;
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeMappingDaySave> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeMappingDaySave workerInstance(ClusterWorkerContext clusterContext) {
            NodeMappingDaySave worker = new NodeMappingDaySave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeMappingDaySave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
