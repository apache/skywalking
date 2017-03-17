package com.a.eye.skywalking.collector.worker.application.receiver;

import com.a.eye.skywalking.collector.actor.*;
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

    public ResponseSummaryReceiver(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFountException {
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
        public ResponseSummaryReceiver workerInstance(ClusterWorkerContext clusterContext) {
            return new ResponseSummaryReceiver(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.ResponseSummaryReceiver.Num;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return ResponseSummaryReceiver.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
