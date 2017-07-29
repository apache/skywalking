package org.skywalking.apm.collector.agentstream.worker.segment;

import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.agentstream.worker.segment.dao.ISegmentDAO;
import org.skywalking.apm.collector.agentstream.worker.segment.define.SegmentDataDefine;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.selector.RollingSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class SegmentPersistenceWorker extends PersistenceWorker {

    public SegmentPersistenceWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected List<?> prepareBatch(Map<String, Data> dataMap) {
        ISegmentDAO dao = (ISegmentDAO)DAOContainer.INSTANCE.get(ISegmentDAO.class.getName());
        return dao.prepareBatch(dataMap);
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<SegmentPersistenceWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SegmentPersistenceWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentPersistenceWorker(role(), clusterContext);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentPersistenceWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }

        @Override public DataDefine dataDefine() {
            return new SegmentDataDefine();
        }
    }
}
