package org.skywalking.apm.collector.agentstream.worker.service.entry;

import org.skywalking.apm.collector.agentstream.worker.service.entry.dao.IServiceEntryDAO;
import org.skywalking.apm.collector.agentstream.worker.service.entry.define.ServiceEntryDataDefine;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.selector.HashCodeSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class ServiceEntryPersistenceWorker extends PersistenceWorker {

    public ServiceEntryPersistenceWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected boolean needMergeDBData() {
        return true;
    }

    @Override protected IPersistenceDAO persistenceDAO() {
        return (IPersistenceDAO)DAOContainer.INSTANCE.get(IServiceEntryDAO.class.getName());
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ServiceEntryPersistenceWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public ServiceEntryPersistenceWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new ServiceEntryPersistenceWorker(role(), clusterContext);
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
            return ServiceEntryPersistenceWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }

        @Override public DataDefine dataDefine() {
            return new ServiceEntryDataDefine();
        }
    }
}
