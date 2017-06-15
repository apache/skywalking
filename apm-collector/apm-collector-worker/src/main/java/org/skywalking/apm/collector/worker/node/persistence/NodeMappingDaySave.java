package org.skywalking.apm.collector.worker.node.persistence;

import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.PersistenceMember;
import org.skywalking.apm.collector.worker.RecordPersistenceMember;
import org.skywalking.apm.collector.worker.node.NodeMappingIndex;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;

/**
 * @author pengys5
 */
public class NodeMappingDaySave extends RecordPersistenceMember {

    NodeMappingDaySave(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
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

    public enum Role implements org.skywalking.apm.collector.actor.Role {
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
