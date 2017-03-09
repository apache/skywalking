package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.storage.MetricPersistenceData;
import com.a.eye.skywalking.collector.worker.tools.PersistenceDataTools;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * @author pengys5
 */
public abstract class MetricPersistenceMember extends PersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(MetricPersistenceMember.class);

    protected MetricPersistenceData persistenceData = new MetricPersistenceData();

    public MetricPersistenceMember(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof MetricPersistenceData) {
            MetricPersistenceData persistenceData = (MetricPersistenceData) message;
            merge(persistenceData);
        } else {
            logger.error("message unhandled");
        }
    }

    public void merge(MetricPersistenceData receiveData) {
        for (Map.Entry<String, Map<String, Long>> lineDate : receiveData.getData().entrySet()) {
            for (Map.Entry<String, Long> columnDate : lineDate.getValue().entrySet()) {
                persistenceData.setMetric(lineDate.getKey(), columnDate.getKey(), columnDate.getValue());
                if (persistenceData.size() >= WorkerConfig.Persistence.Data.size) {
                    persistence();
                }
            }
        }
    }

    protected void persistence() {
        if (persistenceData.size() > 0) {
            Map<String, Map<String, Object>> dataInDB = PersistenceDataTools.searchEs(esIndex(), esType(), persistenceData);
            MetricPersistenceData dbData = PersistenceDataTools.dbData2PersistenceData(dataInDB);
            PersistenceDataTools.mergeData(dbData, persistenceData);

            boolean success = PersistenceDataTools.saveToEs(esIndex(), esType(), persistenceData);
            if (success) {
                persistenceData.clear();
            }
        }
    }
}
