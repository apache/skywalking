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
public class NodeMappingHourSave extends RecordPersistenceMember {

    NodeMappingHourSave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
                        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return NodeMappingIndex.INDEX;
    }

    @Override
    public String esType() {
        return NodeMappingIndex.TYPE_HOUR;
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeMappingHourSave> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeMappingHourSave workerInstance(ClusterWorkerContext clusterContext) {
            NodeMappingHourSave worker = new NodeMappingHourSave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeMappingHourSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
