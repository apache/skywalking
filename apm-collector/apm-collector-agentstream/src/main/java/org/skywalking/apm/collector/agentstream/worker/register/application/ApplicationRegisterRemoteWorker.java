package org.skywalking.apm.collector.agentstream.worker.register.application;

import org.skywalking.apm.collector.agentstream.worker.node.component.NodeComponentAggWorker;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorker;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.stream.worker.selector.ForeverFirstSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class ApplicationRegisterRemoteWorker extends AbstractRemoteWorker {

    protected ApplicationRegisterRemoteWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
    }

    @Override protected void onWork(Object message) throws WorkerException {

    }

    public static class Factory extends AbstractRemoteWorkerProvider<ApplicationRegisterRemoteWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public ApplicationRegisterRemoteWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new ApplicationRegisterRemoteWorker(role(), clusterContext);
        }

        @Override public int workerNum() {
            return 1;
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeComponentAggWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new ForeverFirstSelector();
        }
    }
}
