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
public class NodeRefHourSave extends RecordPersistenceMember {

    NodeRefHourSave(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
                    LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return NodeRefIndex.INDEX;
    }

    @Override
    public String esType() {
        return NodeRefIndex.TYPE_HOUR;
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeRefHourSave> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeRefHourSave workerInstance(ClusterWorkerContext clusterContext) {
            NodeRefHourSave worker = new NodeRefHourSave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefHourSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
