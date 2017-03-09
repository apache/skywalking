package com.a.eye.skywalking.collector.worker.application.analysis;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractAsyncMemberProvider;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.MetricAnalysisMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.receiver.ResponseCostReceiver;
import com.a.eye.skywalking.collector.worker.storage.MetricPersistenceData;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;

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
            long cost = metric.startTime - metric.endTime;
            if (cost <= 1000 && !metric.isError) {
                setMetric(metric.code, metric.second, 1L);
            }
//            logger.debug("response cost metric: %s", data.toString());
        }
    }

    @Override
    protected void aggregation() throws Exception {
        MetricPersistenceData oneMetric;
        while ((oneMetric = pushOneMetric()) != null) {
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

    public static class Metric implements Serializable {
        private final String code;
        private final int second;
        private final Boolean isError;
        private final Long startTime;
        private final Long endTime;

        public Metric(String code, int second, Boolean isError, Long startTime, Long endTime) {
            this.code = code;
            this.second = second;
            this.isError = isError;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
