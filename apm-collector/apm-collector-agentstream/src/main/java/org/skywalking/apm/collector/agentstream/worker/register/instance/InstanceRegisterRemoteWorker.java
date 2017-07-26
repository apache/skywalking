package org.skywalking.apm.collector.agentstream.worker.register.instance;

import org.skywalking.apm.collector.agentstream.worker.register.application.ApplicationDataDefine;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorker;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.selector.ForeverFirstSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class InstanceRegisterRemoteWorker extends AbstractRemoteWorker {

    private final Logger logger = LoggerFactory.getLogger(InstanceRegisterRemoteWorker.class);

    protected InstanceRegisterRemoteWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
    }

    @Override protected void onWork(Object message) throws WorkerException {
        InstanceDataDefine.Instance instance = (InstanceDataDefine.Instance)message;
        logger.debug("application id: {}, agentUUID: {}, register time: {}", instance.getApplicationId(), instance.getAgentUUID(), instance.getRegisterTime());
    }

    public static class Factory extends AbstractRemoteWorkerProvider<InstanceRegisterRemoteWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public InstanceRegisterRemoteWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new InstanceRegisterRemoteWorker(role(), clusterContext);
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return InstanceRegisterRemoteWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new ForeverFirstSelector();
        }

        @Override public DataDefine dataDefine() {
            return new ApplicationDataDefine();
        }
    }
}
