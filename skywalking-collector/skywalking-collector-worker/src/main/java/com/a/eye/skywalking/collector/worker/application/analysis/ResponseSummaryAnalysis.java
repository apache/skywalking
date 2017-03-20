package com.a.eye.skywalking.collector.worker.application.analysis;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.MetricAnalysisMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.receiver.ResponseSummaryReceiver;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ResponseSummaryAnalysis extends MetricAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(ResponseSummaryAnalysis.class);

    public ResponseSummaryAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof Metric) {
            Metric metric = (Metric) message;
            String id = metric.getMinute() + "-" + metric.code;
            setMetric(id, metric.getSecond(), 1L);
//            logger.debug("response summary metric: %s", data.toString());
        }
    }

    @Override
    protected void aggregation() throws Exception {
        MetricData oneMetric;
        while ((oneMetric = pushOne()) != null) {
            getClusterContext().lookup(ResponseSummaryReceiver.Role.INSTANCE).tell(oneMetric);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ResponseSummaryAnalysis> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public ResponseSummaryAnalysis workerInstance(ClusterWorkerContext clusterContext) {
            return new ResponseSummaryAnalysis(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.ResponseSummaryAnalysis.Size;
        }
    }

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return ResponseSummaryAnalysis.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }

    public static class Metric extends AbstractTimeSlice {
        private final String code;
        private final Boolean isError;

        public Metric(long minute, long hour, long day, int second, String code, Boolean isError) {
            super(minute, hour, day, second);
            this.code = code;
            this.isError = isError;
        }
    }
}
