package org.skywalking.apm.collector.worker.globaltrace.persistence;

import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.JoinAndSplitPersistenceMember;
import org.skywalking.apm.collector.worker.globaltrace.GlobalTraceIndex;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;

/**
 * @author pengys5
 */
public class GlobalTraceSave extends JoinAndSplitPersistenceMember {

    GlobalTraceSave(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
                    LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return GlobalTraceIndex.INDEX;
    }

    @Override
    public String esType() {
        return GlobalTraceIndex.TYPE_RECORD;
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<GlobalTraceSave> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public GlobalTraceSave workerInstance(ClusterWorkerContext clusterContext) {
            GlobalTraceSave worker = new GlobalTraceSave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return GlobalTraceSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
