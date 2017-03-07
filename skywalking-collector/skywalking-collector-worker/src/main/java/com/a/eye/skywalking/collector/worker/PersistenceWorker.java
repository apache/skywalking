package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public abstract class PersistenceWorker extends AbstractWorker {

    private Logger logger = LogManager.getFormatterLogger(PersistenceWorker.class);

    private long lastPersistenceTimestamp = 0;

    private Map<String, JsonObject> persistenceData = new HashMap();

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

    public abstract void analyse(Object message) throws Throwable;

    @Override
    public void receive(Object message) throws Throwable {
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
            boolean success = EsClient.saveToEs(esIndex(), esType(), persistenceData);
            if (success) {
                persistenceData.clear();
                lastPersistenceTimestamp = now;
            }
        }
    }
}
