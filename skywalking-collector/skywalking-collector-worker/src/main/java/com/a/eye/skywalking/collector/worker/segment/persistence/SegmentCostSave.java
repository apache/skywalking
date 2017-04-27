package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.RecordPersistenceMember;
import com.a.eye.skywalking.collector.worker.segment.SegmentCostIndex;
import com.a.eye.skywalking.collector.worker.storage.PersistenceWorkerListener;

/**
 * @author pengys5
 */
public class SegmentCostSave extends RecordPersistenceMember {
    @Override
    public String esIndex() {
        return SegmentCostIndex.INDEX;
    }

    @Override
    public String esType() {
        return SegmentCostIndex.TYPE_RECORD;
    }

    protected SegmentCostSave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
                              LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<SegmentCostSave> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public SegmentCostSave workerInstance(ClusterWorkerContext clusterContext) {
            SegmentCostSave worker = new SegmentCostSave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentCostSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
