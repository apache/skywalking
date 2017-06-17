package org.skywalking.apm.collector.worker.instance.persistence;

import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.RecordPersistenceMember;
import org.skywalking.apm.collector.worker.instance.InstanceIndex;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;

public class PingTimeSave extends RecordPersistenceMember {

    public PingTimeSave(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return InstanceIndex.INDEX;
    }

    @Override
    public String esType() {
        return InstanceIndex.TYPE_RECORD;
    }


    public static class Factory extends AbstractLocalSyncWorkerProvider<PingTimeSave> {
        @Override
        public PingTimeSave.Role role() {
            return PingTimeSave.Role.INSTANCE;
        }

        @Override
        public PingTimeSave workerInstance(ClusterWorkerContext clusterContext) {
            PingTimeSave worker = new PingTimeSave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return PingTimeSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
