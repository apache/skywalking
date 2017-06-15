package org.skywalking.apm.collector.worker.instance.persistence;

import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.RecordPersistenceMember;
import org.skywalking.apm.collector.worker.instance.InstanceIndex;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;

public class InstanceSaver extends RecordPersistenceMember {

    public InstanceSaver(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return InstanceIndex.INDEX;
    }

    @Override
    public String esType() {
        return InstanceIndex.TYPE_REGISTRY;
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<InstanceSaver> {
        @Override
        public InstanceSaver.Role role() {
            return InstanceSaver.Role.INSTANCE;
        }

        @Override
        public InstanceSaver workerInstance(ClusterWorkerContext clusterContext) {
            InstanceSaver worker = new InstanceSaver(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return InstanceSaver.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
