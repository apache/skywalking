package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class NodeRefResSumDayAgg extends AbstractClusterWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeRefResSumDayAgg.class);

    NodeRefResSumDayAgg(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(NodeRefResSumDaySave.Role.INSTANCE).create(this);
    }

    @Override
    protected void onWork(Object message) throws Exception {
        if (message instanceof MetricData) {
            getSelfContext().lookup(NodeRefResSumDaySave.Role.INSTANCE).tell(message);
        } else {
            logger.error("message unhandled");
        }
    }

    public static class Factory extends AbstractClusterWorkerProvider<NodeRefResSumDayAgg> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeRefResSumDayAgg workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefResSumDayAgg(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int workerNum() {
            return WorkerConfig.WorkerNum.NodeRef.NodeRefResSumDayAgg.Value;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefResSumDayAgg.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
