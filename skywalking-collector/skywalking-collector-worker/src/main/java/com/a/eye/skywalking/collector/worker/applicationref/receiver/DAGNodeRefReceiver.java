package com.a.eye.skywalking.collector.worker.applicationref.receiver;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.applicationref.persistence.DAGNodeRefPersistence;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class DAGNodeRefReceiver extends AbstractClusterWorker {

    private Logger logger = LogManager.getFormatterLogger(DAGNodeRefReceiver.class);

    private DAGNodeRefPersistence persistence;

    public DAGNodeRefReceiver(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(DAGNodeRefPersistence.Role.INSTANCE).create(this);
    }

    @Override
    public void work(Object message) throws Exception {
        if (message instanceof RecordData) {
            getSelfContext().lookup(DAGNodeRefPersistence.Role.INSTANCE).tell(message);
        } else {
            logger.error("message unhandled");
        }
    }

    public static class Factory extends AbstractClusterWorkerProvider<DAGNodeRefReceiver> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public DAGNodeRefReceiver workerInstance(ClusterWorkerContext clusterContext) {
            return new DAGNodeRefReceiver(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.DAGNodeRefReceiver.Num;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return DAGNodeRefReceiver.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
