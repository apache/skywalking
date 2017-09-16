package org.skywalking.apm.collector.agentregister.worker.servicename;

import org.skywalking.apm.collector.storage.define.register.ServiceNameDataDefine;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorker;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.stream.worker.selector.ForeverFirstSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ServiceNameRegisterRemoteWorker extends AbstractRemoteWorker {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameRegisterRemoteWorker.class);

    protected ServiceNameRegisterRemoteWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
    }

    @Override protected void onWork(Object message) throws WorkerException {
        ServiceNameDataDefine.ServiceName serviceName = (ServiceNameDataDefine.ServiceName)message;
        logger.debug("service name: {}", serviceName.getServiceName());
        getClusterContext().lookup(ServiceNameRegisterSerialWorker.WorkerRole.INSTANCE).tell(serviceName);
    }

    public static class Factory extends AbstractRemoteWorkerProvider<ServiceNameRegisterRemoteWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public ServiceNameRegisterRemoteWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new ServiceNameRegisterRemoteWorker(role(), clusterContext);
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return ServiceNameRegisterRemoteWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new ForeverFirstSelector();
        }

        @Override public DataDefine dataDefine() {
            return new ServiceNameDataDefine();
        }
    }
}
