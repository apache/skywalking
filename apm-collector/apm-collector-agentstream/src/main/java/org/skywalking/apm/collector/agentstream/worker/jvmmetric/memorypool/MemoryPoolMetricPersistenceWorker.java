package org.skywalking.apm.collector.agentstream.worker.jvmmetric.memorypool;

import org.skywalking.apm.collector.agentstream.worker.jvmmetric.memorypool.dao.IMemoryPoolMetricDAO;
import org.skywalking.apm.collector.agentstream.worker.jvmmetric.memorypool.define.MemoryPoolMetricDataDefine;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.selector.RollingSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class MemoryPoolMetricPersistenceWorker extends PersistenceWorker {

    public MemoryPoolMetricPersistenceWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected boolean needMergeDBData() {
        return false;
    }

    @Override protected IPersistenceDAO persistenceDAO() {
        return (IPersistenceDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<MemoryPoolMetricPersistenceWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public MemoryPoolMetricPersistenceWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new MemoryPoolMetricPersistenceWorker(role(), clusterContext);
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
            return MemoryPoolMetricPersistenceWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }

        @Override public DataDefine dataDefine() {
            return new MemoryPoolMetricDataDefine();
        }
    }
}
