package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractMember;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.JsonObject;
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
public abstract class PersistenceMember<T> extends AbstractMember<T> {

    private Logger logger = LogManager.getFormatterLogger(PersistenceMember.class);

    private long lastPersistenceTimestamp = 0;

    private Map<String, JsonObject> persistenceData = new HashMap();

    public PersistenceMember(ActorRef actorRef) {
        super(actorRef);
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
            logger.debug("receive message");
            analyse(message);
        }
    }

    private void persistence(boolean dataFull) {
        long now = System.currentTimeMillis();
        if (now - lastPersistenceTimestamp > 5000 || dataFull) {
            boolean success = saveToEs();
            if (success) {
                persistenceData.clear();
                lastPersistenceTimestamp = now;
            }
        }
    }

    private boolean saveToEs() {
        TransportClient client = EsClient.client();
        BulkRequestBuilder bulkRequest = client.prepareBulk();

        for (Map.Entry<String, JsonObject> entry : persistenceData.entrySet()) {
            String id = entry.getKey();
            JsonObject data = entry.getValue();
            bulkRequest.add(client.prepareIndex(esIndex(), esType(), id).setSource(data.toString()));
        }

        BulkResponse bulkResponse = bulkRequest.execute().actionGet();
        return !bulkResponse.hasFailures();
    }
}
