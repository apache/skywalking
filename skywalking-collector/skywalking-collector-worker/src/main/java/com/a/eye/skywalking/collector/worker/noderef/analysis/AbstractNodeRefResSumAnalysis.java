package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.MetricAnalysisMember;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.trace.TraceSegment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public abstract class AbstractNodeRefResSumAnalysis extends MetricAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(AbstractNodeRefResSumAnalysis.class);

    public AbstractNodeRefResSumAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    public void analyseResSum(TraceSegment segment, long timeSlice) throws Exception {
//        if (message instanceof NodeRefResRecord) {
//            Metric metric = (Metric) message;
//            String id = metric.getMinute() + "-" + metric.code;
//            setMetric(id, metric.getSecond(), 1L);
//            logger.debug("response summary metric: %s", data.toString());
//        }
    }

    public static class NodeRefResRecord extends AbstractTimeSlice {
        private String nodeRefId;
        private long startTime;
        private long endTime;
        private Boolean isError;

        public NodeRefResRecord(long minute, long hour, long day, int second) {
            super(minute, hour, day, second);
        }
    }
}
