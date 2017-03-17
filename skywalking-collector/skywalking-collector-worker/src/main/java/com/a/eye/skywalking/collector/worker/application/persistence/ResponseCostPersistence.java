package com.a.eye.skywalking.collector.worker.application.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.MetricPersistenceMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ResponseCostPersistence extends MetricPersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(ResponseCostPersistence.class);

    public ResponseCostPersistence(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return "application_metric";
    }

    @Override
    public String esType() {
        return "response_cost";
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ResponseCostPersistence> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public ResponseCostPersistence workerInstance(ClusterWorkerContext clusterContext) {
            return new ResponseCostPersistence(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Persistence.ResponseCostPersistence.Size;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return ResponseCostPersistence.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
