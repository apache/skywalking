package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.storage.RecordPersistenceData;
import com.google.gson.JsonObject;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * @author pengys5
 */
public abstract class RecordAnalysisMember extends AnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(RecordAnalysisMember.class);

    private RecordPersistenceData persistenceData = new RecordPersistenceData();

    public RecordAnalysisMember(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    public void setRecord(String id, JsonObject record) throws Exception {
        persistenceData.setMetric(id, record);
        if (persistenceData.size() >= WorkerConfig.Analysis.Data.size) {
            aggregation();
        }
    }

    public RecordPersistenceData pushOneRecord() {
        if (persistenceData.getData().entrySet().iterator().hasNext()) {
            Map.Entry<String, JsonObject> entry = persistenceData.getData().entrySet().iterator().next();
            RecordPersistenceData oneRecord = new RecordPersistenceData();
            oneRecord.setMetric(entry.getKey(), entry.getValue());
            oneRecord.setHashCode(entry.getKey());

            persistenceData.getData().remove(entry.getKey());
            return oneRecord;
        }
        return null;
    }
}
