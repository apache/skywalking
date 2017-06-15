package org.skywalking.apm.collector.worker.segment.persistence;

import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.PersistenceMember;
import org.skywalking.apm.collector.worker.RecordPersistenceMember;
import org.skywalking.apm.collector.worker.segment.SegmentExceptionIndex;
import org.skywalking.apm.collector.worker.storage.AbstractIndex;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;

/**
 * @author pengys5
 */
public class SegmentExceptionSave extends RecordPersistenceMember {
    @Override
    public String esIndex() {
        return SegmentExceptionIndex.INDEX;
    }

    @Override
    public String esType() {
        return AbstractIndex.TYPE_RECORD;
    }

    protected SegmentExceptionSave(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
                                   LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<SegmentExceptionSave> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public SegmentExceptionSave workerInstance(ClusterWorkerContext clusterContext) {
            SegmentExceptionSave worker = new SegmentExceptionSave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentExceptionSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
