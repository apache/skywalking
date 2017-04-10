package com.a.eye.skywalking.collector.worker.globaltrace.persistence;


import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.MergePersistenceMember;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.globaltrace.GlobalTraceIndex;

/**
 * @author pengys5
 */
public class GlobalTraceSave extends MergePersistenceMember {

    GlobalTraceSave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return GlobalTraceIndex.Index;
    }

    @Override
    public String esType() {
        return GlobalTraceIndex.Type_Record;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<GlobalTraceSave> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.GlobalTrace.GlobalTraceSave.Size;
        }

        @Override
        public GlobalTraceSave workerInstance(ClusterWorkerContext clusterContext) {
            return new GlobalTraceSave(role(), clusterContext, new LocalWorkerContext());
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
