package org.skywalking.apm.collector.worker.noderef.persistence;

import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.MetricPersistenceMember;
import org.skywalking.apm.collector.worker.noderef.NodeRefResSumIndex;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;

/**
 * @author pengys5
 */
public class NodeRefResSumMinuteSave extends MetricPersistenceMember {

    NodeRefResSumMinuteSave(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
                            LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return NodeRefResSumIndex.INDEX;
    }

    @Override
    public String esType() {
        return NodeRefResSumIndex.TYPE_MINUTE;
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeRefResSumMinuteSave> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeRefResSumMinuteSave workerInstance(ClusterWorkerContext clusterContext) {
            NodeRefResSumMinuteSave worker = new NodeRefResSumMinuteSave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefResSumMinuteSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
