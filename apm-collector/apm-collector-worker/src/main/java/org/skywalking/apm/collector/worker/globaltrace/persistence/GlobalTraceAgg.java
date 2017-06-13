package org.skywalking.apm.collector.worker.globaltrace.persistence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.*;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.storage.JoinAndSplitData;

/**
 * @author pengys5
 */
public class GlobalTraceAgg extends AbstractClusterWorker {

    private Logger logger = LogManager.getFormatterLogger(GlobalTraceAgg.class);

    GlobalTraceAgg(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
                   LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(GlobalTraceSave.Role.INSTANCE).create(this);
    }

    @Override
    protected void onWork(Object message) throws WorkerException {
        if (message instanceof JoinAndSplitData) {
            getSelfContext().lookup(GlobalTraceSave.Role.INSTANCE).tell(message);
        } else {
            logger.error("unhandled message, message instance must JoinAndSplitData, but is %s", message.getClass().toString());
        }
    }

    public static class Factory extends AbstractClusterWorkerProvider<GlobalTraceAgg> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public GlobalTraceAgg workerInstance(ClusterWorkerContext clusterContext) {
            return new GlobalTraceAgg(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int workerNum() {
            return WorkerConfig.WorkerNum.GlobalTrace.GlobalTraceAgg.VALUE;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return GlobalTraceAgg.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
