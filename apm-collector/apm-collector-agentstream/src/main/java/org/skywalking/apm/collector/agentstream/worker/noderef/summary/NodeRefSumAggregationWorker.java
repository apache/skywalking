package org.skywalking.apm.collector.agentstream.worker.noderef.summary;

import org.skywalking.apm.collector.storage.define.noderef.NodeRefSumDataDefine;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.collector.stream.worker.WorkerRefs;
import org.skywalking.apm.collector.stream.worker.impl.AggregationWorker;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.stream.worker.selector.HashCodeSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class NodeRefSumAggregationWorker extends AggregationWorker {

    public NodeRefSumAggregationWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected WorkerRefs nextWorkRef(String id) throws WorkerNotFoundException {
        return getClusterContext().lookup(NodeRefSumRemoteWorker.WorkerRole.INSTANCE);
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeRefSumAggregationWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeRefSumAggregationWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefSumAggregationWorker(role(), clusterContext);
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
            return NodeRefSumAggregationWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }

        @Override public DataDefine dataDefine() {
            return new NodeRefSumDataDefine();
        }
    }
}
