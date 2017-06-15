package org.skywalking.apm.collector.worker.instance.persistence;

import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.RecordPersistenceMember;
import org.skywalking.apm.collector.worker.instance.PingTimeIndex;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;

public class PingTimeUpdater extends RecordPersistenceMember {

    public PingTimeUpdater(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return PingTimeIndex.INDEX;
    }

    @Override
    public String esType() {
        return PingTimeIndex.TYPE_PING_TIME;
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<PingTimeUpdater> {
        @Override
        public PingTimeUpdater.Role role() {
            return PingTimeUpdater.Role.INSTANCE;
        }

        @Override
        public PingTimeUpdater workerInstance(ClusterWorkerContext clusterContext) {
            PingTimeUpdater worker = new PingTimeUpdater(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return PingTimeUpdater.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
