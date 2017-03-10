package com.a.eye.skywalking.collector.worker.application.persistence;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractAsyncMemberProvider;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.MetricPersistenceMember;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ResponseSummaryPersistence extends MetricPersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(ResponseSummaryPersistence.class);

    public ResponseSummaryPersistence(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    @Override
    public String esIndex() {
        return "application_metric";
    }

    @Override
    public String esType() {
        return "response_summary";
    }

    public static class Factory extends AbstractAsyncMemberProvider<ResponseSummaryPersistence> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class memberClass() {
            return ResponseSummaryPersistence.class;
        }

        @Override
        public int queueSize() {
            return WorkerConfig.Queue.Persistence.ResponseSummaryPersistence.Size;
        }
    }
}
