package com.a.eye.skywalking.collector.worker.applicationref.receiver;

import com.a.eye.skywalking.collector.actor.AbstractClusterWorker;
import com.a.eye.skywalking.collector.actor.AbstractClusterWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
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

    public DAGNodeRefReceiver(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }

    @Override
    public void preStart() throws Exception {
        getClusterContext().findProvider(DAGNodeRefPersistence.Role.INSTANCE).create(getClusterContext(), getSelfContext());
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
        public Class workerClass() {
            return DAGNodeRefReceiver.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.DAGNodeRefReceiver.Num;
        }
    }

    public static class Role extends com.a.eye.skywalking.collector.actor.Role {
        public static Role INSTANCE = new Role();

        @Override
        public String name() {
            return DAGNodeRefReceiver.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
