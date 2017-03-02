package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.worker.PersistenceCommand;
import com.a.eye.skywalking.collector.worker.tools.EsClient;
import com.google.gson.JsonObject;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public abstract class PersistenceWorker<T> extends AbstractWorker<T> {

    private long lastPersistenceTimestamp = 0;

    private Map<String, JsonObject> persistenceData = new HashMap();

    public abstract String esIndex();

    public abstract String esType();

    public void putData(String id, JsonObject data) {
        persistenceData.put(id, data);
        if (persistenceData.size() >= 1000) {
            persistence(true);
        }
    }

    public boolean containsId(String id) {
        return persistenceData.containsKey(id);
    }

    public JsonObject getData(String id) {
        return persistenceData.get(id);
    }

    public abstract void analyse(Object message) throws Throwable;

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof PersistenceCommand) {
            persistence(false);
        } else {
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
        BulkRequestBuilder bulkRequest = EsClient.client().prepareBulk();

        for (Map.Entry<String, JsonObject> entry : persistenceData.entrySet()) {
            String id = entry.getKey();
            JsonObject data = entry.getValue();
            bulkRequest.add(EsClient.client().prepareIndex(esIndex(), esType(), id).setSource(data.toString()));
        }

        BulkResponse bulkResponse = bulkRequest.execute().actionGet();
        return !bulkResponse.hasFailures();
    }
}
