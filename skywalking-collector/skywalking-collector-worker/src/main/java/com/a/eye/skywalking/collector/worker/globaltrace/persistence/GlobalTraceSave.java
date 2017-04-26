package com.a.eye.skywalking.collector.worker.globaltrace.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.JoinAndSplitPersistenceMember;
import com.a.eye.skywalking.collector.worker.globaltrace.GlobalTraceIndex;
import com.a.eye.skywalking.collector.worker.storage.PersistenceWorkerListener;

/**
 * @author pengys5
 */
public class GlobalTraceSave extends JoinAndSplitPersistenceMember {

    GlobalTraceSave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
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

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
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
