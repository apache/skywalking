package com.a.eye.skywalking.collector.worker.node.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordPersistenceMember;
import com.a.eye.skywalking.collector.worker.node.NodeCompIndex;
import com.a.eye.skywalking.collector.worker.storage.PersistenceWorkerListener;

/**
 * @author pengys5
 */
public class NodeCompSave extends RecordPersistenceMember {

    NodeCompSave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
                 LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return NodeCompIndex.INDEX;
    }

    @Override
    public String esType() {
        return NodeCompIndex.TYPE_RECORD;
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeCompSave> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeCompSave workerInstance(ClusterWorkerContext clusterContext) {
            NodeCompSave worker = new NodeCompSave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeCompSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
