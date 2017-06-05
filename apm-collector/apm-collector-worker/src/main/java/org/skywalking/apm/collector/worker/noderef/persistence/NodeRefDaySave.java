package org.skywalking.apm.collector.worker.noderef.persistence;

import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.RecordPersistenceMember;
import org.skywalking.apm.collector.worker.noderef.NodeRefIndex;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;

/**
 * @author pengys5
 */
public class NodeRefDaySave extends RecordPersistenceMember {

    NodeRefDaySave(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
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

    public enum Role implements org.skywalking.apm.collector.actor.Role {
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
