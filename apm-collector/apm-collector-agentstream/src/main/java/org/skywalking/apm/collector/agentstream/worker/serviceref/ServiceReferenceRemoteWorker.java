package org.skywalking.apm.collector.agentstream.worker.serviceref;

import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceDataDefine;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorker;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.stream.worker.selector.HashCodeSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class ServiceReferenceRemoteWorker extends AbstractRemoteWorker {

    protected ServiceReferenceRemoteWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {

    }

    @Override protected void onWork(Object message) throws WorkerException {
        getClusterContext().lookup(ServiceReferencePersistenceWorker.WorkerRole.INSTANCE).tell(message);
    }

    public static class Factory extends AbstractRemoteWorkerProvider<ServiceReferenceRemoteWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public ServiceReferenceRemoteWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new ServiceReferenceRemoteWorker(role(), clusterContext);
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return ServiceReferenceRemoteWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }

        @Override public DataDefine dataDefine() {
            return new ServiceReferenceDataDefine();
        }
    }
}
