package com.a.eye.skywalking.collector.worker.application.analysis;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.MetricAnalysisMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.receiver.ResponseCostReceiver;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ResponseCostAnalysis extends MetricAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(ResponseCostAnalysis.class);

    public ResponseCostAnalysis(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof Metric) {
            Metric metric = (Metric) message;
            long cost = metric.endTime - metric.startTime;
            if (cost <= 1000 && !metric.isError) {
                String id = metric.getMinute() + "-" + metric.code;
                setMetric(id, metric.getSecond(), cost);
            }
//            logger.debug("response cost metric: %s", data.toString());
        }
    }

    @Override
    protected void aggregation() throws Exception {
        MetricData oneMetric;
        while ((oneMetric = pushOne()) != null) {
            getClusterContext().lookup(ResponseCostReceiver.Role.INSTANCE).tell(oneMetric);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ResponseCostAnalysis> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public Class workerClass() {
            return ResponseCostAnalysis.class;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.ResponseCostAnalysis.Size;
        }
    }

    public static class Role extends com.a.eye.skywalking.collector.actor.Role {
        public static Role INSTANCE = new Role();

        @Override
        public String name() {
            return ResponseCostAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }

    public static class Metric extends AbstractTimeSlice {
        private final String code;
        private final Boolean isError;
        private final Long startTime;
        private final Long endTime;

        public Metric(long minute, int second, String code, Boolean isError, Long startTime, Long endTime) {
            super(minute, second);
            this.code = code;
            this.isError = isError;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
