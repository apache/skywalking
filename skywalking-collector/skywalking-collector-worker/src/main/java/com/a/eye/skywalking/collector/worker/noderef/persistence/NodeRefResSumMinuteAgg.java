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
public class NodeRefResSumMinuteAgg extends AbstractClusterWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeRefResSumMinuteAgg.class);

    NodeRefResSumMinuteAgg(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(NodeRefResSumMinuteSave.Role.INSTANCE).create(this);
    }

    @Override
    protected void onWork(Object message) throws Exception {
        if (message instanceof MetricData) {
            getSelfContext().lookup(NodeRefResSumMinuteSave.Role.INSTANCE).tell(message);
        } else {
            logger.error("unhandled message, message instance must MetricData, but is %s", message.getClass().toString());
        }
    }

    public static class Factory extends AbstractClusterWorkerProvider<NodeRefResSumMinuteAgg> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeRefResSumMinuteAgg workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefResSumMinuteAgg(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int workerNum() {
            return WorkerConfig.WorkerNum.NodeRef.NodeRefResSumMinuteAgg.VALUE;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefResSumMinuteAgg.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
