package org.skywalking.apm.collector.agentstream.worker.node.component;

import org.skywalking.apm.collector.agentstream.worker.cache.ComponentCache;
import org.skywalking.apm.collector.agentstream.worker.node.component.define.NodeComponentDataDefine;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.collector.stream.worker.impl.ExchangeWorker;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.selector.HashCodeSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class NodeComponentExchangeWorker extends ExchangeWorker {

    private final Logger logger = LoggerFactory.getLogger(NodeComponentExchangeWorker.class);

    public NodeComponentExchangeWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected void exchange(Data data) {
        NodeComponentDataDefine.NodeComponent nodeComponent = new NodeComponentDataDefine.NodeComponent();
        nodeComponent.toSelf(data);

        int componentId = ComponentCache.get(nodeComponent.getApplicationId(), nodeComponent.getComponentName());
        if (componentId == 0 && nodeComponent.getTimes() < 10) {
            try {
                nodeComponent.increase();
                getClusterContext().lookup(NodeComponentExchangeWorker.WorkerRole.INSTANCE).tell(nodeComponent.toData());
            } catch (WorkerNotFoundException | WorkerInvokeException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeComponentExchangeWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeComponentExchangeWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeComponentExchangeWorker(role(), clusterContext);
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
            return NodeComponentExchangeWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }

        @Override public DataDefine dataDefine() {
            return new NodeComponentDataDefine();
        }
    }
}
