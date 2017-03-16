package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.storage.RecordPersistenceData;
import com.google.gson.JsonObject;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public abstract class RecordAnalysisMember extends AnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(RecordAnalysisMember.class);

    private RecordPersistenceData persistenceData = new RecordPersistenceData();

    public RecordAnalysisMember(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }

    public void setRecord(String id, JsonObject record) throws Exception {
        persistenceData.getElseCreate(id).setRecord(record);
        if (persistenceData.size() >= WorkerConfig.Analysis.Data.size) {
            aggregation();
        }
    }

    public RecordData pushOne() {
        if (persistenceData.hasNext()) {
            return persistenceData.pushOne();
        }
        return null;
    }
}
