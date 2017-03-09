package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.storage.RecordPersistenceData;
import com.a.eye.skywalking.collector.worker.tools.PersistenceDataTools;
import com.google.gson.JsonObject;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * @author pengys5
 */
public abstract class RecordPersistenceMember extends PersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(RecordPersistenceMember.class);

    protected RecordPersistenceData persistenceData = new RecordPersistenceData();

    public RecordPersistenceMember(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    public void setRecord(String id, JsonObject record) {
        persistenceData.setMetric(id, record);
        if (persistenceData.size() >= WorkerConfig.Persistence.Data.size) {
            persistence();
        }
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof RecordPersistenceData) {
            RecordPersistenceData persistenceData = (RecordPersistenceData) message;
            merge(persistenceData);
        } else {
            logger.error("message unhandled");
        }
    }

    public void merge(RecordPersistenceData receiveData) {
        for (Map.Entry<String, JsonObject> lineDate : receiveData.getData().entrySet()) {
            persistenceData.setMetric(lineDate.getKey(), lineDate.getValue());
            if (persistenceData.size() >= WorkerConfig.Persistence.Data.size) {
                persistence();
            }
        }
    }

    protected void persistence() {
        if (persistenceData.size() > 0) {
            boolean success = PersistenceDataTools.saveToEs(esIndex(), esType(), persistenceData);
            if (success) {
                persistenceData.clear();
            }
        }
    }
}
