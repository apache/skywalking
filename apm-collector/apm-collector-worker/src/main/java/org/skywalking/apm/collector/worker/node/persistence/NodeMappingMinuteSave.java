package org.skywalking.apm.collector.worker.node.persistence;

import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.RecordPersistenceMember;
import org.skywalking.apm.collector.worker.node.NodeMappingIndex;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;

/**
 * @author pengys5
 */
public class NodeMappingMinuteSave extends RecordPersistenceMember {

    NodeMappingMinuteSave(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
                          LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return NodeMappingIndex.INDEX;
    }

    @Override
    public String esType() {
        return NodeMappingIndex.TYPE_MINUTE;
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeMappingMinuteSave> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeMappingMinuteSave workerInstance(ClusterWorkerContext clusterContext) {
            NodeMappingMinuteSave worker = new NodeMappingMinuteSave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeMappingMinuteSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
