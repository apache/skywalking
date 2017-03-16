package com.a.eye.skywalking.collector.worker.application.receiver;

import com.a.eye.skywalking.collector.actor.AbstractClusterWorker;
import com.a.eye.skywalking.collector.actor.AbstractClusterWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.persistence.ResponseSummaryPersistence;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ResponseSummaryReceiver extends AbstractClusterWorker {

    private Logger logger = LogManager.getFormatterLogger(ResponseSummaryReceiver.class);

    public ResponseSummaryReceiver(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }

    @Override
    public void preStart() throws Exception {
        getClusterContext().findProvider(ResponseSummaryPersistence.Role.INSTANCE).create(getClusterContext(), getSelfContext());
    }

    @Override
    public void work(Object message) throws Exception {
        if (message instanceof MetricData) {
            getSelfContext().lookup(ResponseSummaryPersistence.Role.INSTANCE).tell(message);
        } else {
            logger.error("message unhandled");
        }
    }

    public static class Factory extends AbstractClusterWorkerProvider<ResponseSummaryReceiver> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return null;
        }

        @Override
        public Class workerClass() {
            return ResponseSummaryReceiver.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.ResponseSummaryReceiver.Num;
        }
    }

    public static class Role extends com.a.eye.skywalking.collector.actor.Role {
        public static Role INSTANCE = new Role();

        @Override
        public String name() {
            return ResponseSummaryReceiver.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
