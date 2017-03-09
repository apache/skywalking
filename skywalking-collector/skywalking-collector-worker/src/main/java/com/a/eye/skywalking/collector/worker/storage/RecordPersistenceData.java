package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.AbstractHashMessage;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class RecordPersistenceData extends AbstractHashMessage {
    private Map<String, JsonObject> persistenceData = new HashMap();

    public void setMetric(String id, JsonObject record) {
        persistenceData.put(id, record);
    }

    public Map<String, JsonObject> getData() {
        return persistenceData;
    }

    public int size() {
        return persistenceData.size();
    }

    public void clear() {
        persistenceData.clear();
    }
}
