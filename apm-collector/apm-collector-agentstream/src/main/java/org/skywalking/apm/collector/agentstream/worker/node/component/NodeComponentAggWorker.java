package org.skywalking.apm.collector.agentstream.worker.node.component;

import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.impl.AggregationWorker;
import org.skywalking.apm.collector.stream.worker.selector.RollingSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class NodeComponentAggWorker extends AggregationWorker {

    public NodeComponentAggWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected void sendToNext() {

    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeComponentAggWorker> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeComponentAggWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeComponentAggWorker(role(), clusterContext);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    public enum Role implements org.skywalking.apm.collector.stream.worker.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeComponentAggWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
