package com.a.eye.skywalking.collector.worker.application.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.MetricPersistenceMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ResponseSummaryPersistence extends MetricPersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(ResponseSummaryPersistence.class);

    public ResponseSummaryPersistence(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }

    @Override
    public String esIndex() {
        return "application_metric";
    }

    @Override
    public String esType() {
        return "response_summary";
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ResponseSummaryPersistence> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return null;
        }

        @Override
        public Class workerClass() {
            return ResponseSummaryPersistence.class;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Persistence.ResponseSummaryPersistence.Size;
        }
    }

    public static class Role extends com.a.eye.skywalking.collector.actor.Role {
        public static Role INSTANCE = new Role();

        @Override
        public String name() {
            return ResponseSummaryPersistence.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
