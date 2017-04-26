package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.MetricPersistenceMember;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefResSumIndex;
import com.a.eye.skywalking.collector.worker.storage.PersistenceWorkerListener;

/**
 * @author pengys5
 */
public class NodeRefResSumDaySave extends MetricPersistenceMember {

    NodeRefResSumDaySave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
                         LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return NodeRefResSumIndex.INDEX;
    }

    @Override
    public String esType() {
        return NodeRefResSumIndex.TYPE_DAY;
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeRefResSumDaySave> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeRefResSumDaySave workerInstance(ClusterWorkerContext clusterContext) {
            NodeRefResSumDaySave worker = new NodeRefResSumDaySave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefResSumDaySave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
