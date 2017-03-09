package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.storage.MetricPersistenceData;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

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
        persistenceData.setMetric(id, second, value);
        if (persistenceData.size() >= WorkerConfig.Persistence.Data.size) {
            aggregation();
        }
    }

    public MetricPersistenceData pushOneMetric() {
        if (persistenceData.getData().entrySet().iterator().hasNext()) {
            Map.Entry<String, Map<String, Long>> entry = persistenceData.getData().entrySet().iterator().next();
            MetricPersistenceData oneRecord = new MetricPersistenceData();
            for (Map.Entry<String, Long> entry1 : entry.getValue().entrySet()) {
                oneRecord.setMetric(entry.getKey(), entry1.getKey(), entry1.getValue());
            }
            oneRecord.setHashCode(entry.getKey());

            persistenceData.getData().remove(entry.getKey());
            return oneRecord;
        }
        return null;
    }
}
