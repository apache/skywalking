package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordPersistenceMember;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefIndex;
import com.a.eye.skywalking.collector.worker.storage.PersistenceWorkerListener;

/**
 * @author pengys5
 */
public class NodeRefDaySave extends RecordPersistenceMember {

    NodeRefDaySave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
                   LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return NodeRefIndex.INDEX;
    }

    @Override
    public String esType() {
        return NodeRefIndex.TYPE_DAY;
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeRefDaySave> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeRefDaySave workerInstance(ClusterWorkerContext clusterContext) {
            NodeRefDaySave worker = new NodeRefDaySave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefDaySave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
