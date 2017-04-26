package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class NodeRefHourAgg extends AbstractClusterWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeRefHourAgg.class);

    NodeRefHourAgg(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(NodeRefHourSave.Role.INSTANCE).create(this);
    }

    @Override
    protected void onWork(Object message) throws Exception {
        if (message instanceof RecordData) {
            getSelfContext().lookup(NodeRefHourSave.Role.INSTANCE).tell(message);
        } else {
            logger.error("unhandled message, message instance must RecordData, but is %s", message.getClass().toString());
        }
    }

    public static class Factory extends AbstractClusterWorkerProvider<NodeRefHourAgg> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public NodeRefHourAgg workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefHourAgg(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int workerNum() {
            return WorkerConfig.WorkerNum.NodeRef.NodeRefHourAgg.VALUE;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefHourAgg.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
