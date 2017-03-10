package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import com.a.eye.skywalking.collector.worker.storage.MetricPersistenceData;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public abstract class MetricAnalysisMember extends AnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(MetricAnalysisMember.class);

    protected MetricPersistenceData persistenceData = new MetricPersistenceData();

    public MetricAnalysisMember(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    public void setMetric(String id, int second, Long value) throws Exception {
        persistenceData.getElseCreate(id).setMetric(second, value);
        if (persistenceData.size() >= WorkerConfig.Persistence.Data.size) {
            aggregation();
        }
    }

    public MetricData pushOne() {
        if (persistenceData.iterator().hasNext()) {
            return persistenceData.pushOne();
        }
        return null;
    }
}
