package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractAsyncMember;
import com.a.eye.skywalking.collector.actor.AbstractMember;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.JsonObject;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.transport.TransportClient;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public abstract class PersistenceMember extends AbstractAsyncMember {

    private Logger logger = LogManager.getFormatterLogger(PersistenceMember.class);

    private long lastPersistenceTimestamp = 0;

    private Map<String, JsonObject> persistenceData = new HashMap();

    public PersistenceMember(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    public abstract String esIndex();

    public abstract String esType();

    public void putData(String id, JsonObject data) {
        persistenceData.put(id, data);
//        if (persistenceData.size() >= 1000) {
//            persistence(true);
//        }
    }

    public boolean containsId(String id) {
        return persistenceData.containsKey(id);
    }

    public JsonObject getData(String id) {
        return persistenceData.get(id);
    }

    public abstract void analyse(Object message) throws Exception;

    @Override
    public void receive(Object message) throws Exception {
        if (message instanceof PersistenceCommand) {
            persistence(false);
        } else {
            analyse(message);
        }
    }

    private void persistence(boolean dataFull) {
        long now = System.currentTimeMillis();
        if (now - lastPersistenceTimestamp > 5000 || dataFull) {
            boolean success = EsClient.saveToEs(esIndex(), esType(), persistenceData);
            if (success) {
                persistenceData.clear();
                lastPersistenceTimestamp = now;
            }
        }
    }
}
