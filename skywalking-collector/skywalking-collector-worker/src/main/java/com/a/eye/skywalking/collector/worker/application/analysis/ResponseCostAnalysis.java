package com.a.eye.skywalking.collector.worker.application.analysis;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractAsyncMemberProvider;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.MetricAnalysisMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.receiver.ResponseCostReceiver;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ResponseCostAnalysis extends MetricAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(ResponseCostAnalysis.class);

    public ResponseCostAnalysis(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
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
            tell(ResponseCostReceiver.Factory.INSTANCE, HashCodeSelector.INSTANCE, oneMetric);
        }
    }

    public static class Factory extends AbstractAsyncMemberProvider<ResponseCostAnalysis> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class memberClass() {
            return ResponseCostAnalysis.class;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.ResponseCostAnalysis.Size;
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
