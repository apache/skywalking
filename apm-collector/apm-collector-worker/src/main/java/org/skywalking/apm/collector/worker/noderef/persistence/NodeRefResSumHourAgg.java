package org.skywalking.apm.collector.worker.noderef.persistence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.*;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.storage.MetricData;

/**
 * @author pengys5
 */
public class NodeRefResSumHourAgg extends AbstractClusterWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeRefResSumHourAgg.class);

    NodeRefResSumHourAgg(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
                         LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(NodeRefResSumHourSave.Role.INSTANCE).create(this);
    }

    @Override
    protected void onWork(Object message) throws Exception {
        if (message instanceof MetricData) {
            getSelfContext().lookup(NodeRefResSumHourSave.Role.INSTANCE).tell(message);
        } else {
            logger.error("unhandled message, message instance must MetricData, but is %s", message.getClass().toString());
        }
    }

    public static class Factory extends AbstractClusterWorkerProvider<NodeRefResSumHourAgg> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeRefResSumHourAgg workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefResSumHourAgg(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int workerNum() {
            return WorkerConfig.WorkerNum.NodeRef.NodeRefResSumHourAgg.VALUE;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefResSumHourAgg.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
