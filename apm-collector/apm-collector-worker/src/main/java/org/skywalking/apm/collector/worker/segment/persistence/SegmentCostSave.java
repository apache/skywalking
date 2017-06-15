package org.skywalking.apm.collector.worker.segment.persistence;

import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.PersistenceMember;
import org.skywalking.apm.collector.worker.RecordPersistenceMember;
import org.skywalking.apm.collector.worker.segment.SegmentCostIndex;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;

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

    protected SegmentCostSave(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
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

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentCostSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
