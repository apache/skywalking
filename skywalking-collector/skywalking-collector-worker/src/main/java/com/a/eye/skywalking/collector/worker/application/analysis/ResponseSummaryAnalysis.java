package com.a.eye.skywalking.collector.worker.application.analysis;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractAsyncMemberProvider;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.MetricAnalysisMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.receiver.ResponseSummaryReceiver;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ResponseSummaryAnalysis extends MetricAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(ResponseSummaryAnalysis.class);

    public ResponseSummaryAnalysis(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
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
            tell(ResponseSummaryReceiver.Factory.INSTANCE, HashCodeSelector.INSTANCE, oneMetric);
        }
    }

    public static class Factory extends AbstractAsyncMemberProvider<ResponseSummaryAnalysis> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class memberClass() {
            return ResponseSummaryAnalysis.class;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.ResponseSummaryAnalysis.Size;
        }
    }

    public static class Metric extends AbstractTimeSlice {
        private final String code;
        private final Boolean isError;

        public Metric(long minute, int second, String code, Boolean isError) {
            super(minute, second);
            this.code = code;
            this.isError = isError;
        }
    }
}
