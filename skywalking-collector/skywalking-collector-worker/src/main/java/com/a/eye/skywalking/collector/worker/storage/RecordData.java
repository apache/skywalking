package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.selector.AbstractHashMessage;
import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public class RecordData extends AbstractHashMessage {

    private String id;
    private JsonObject record;

    public RecordData(String key) {
        super(key);
        this.id = key;
    }

    public String getId() {
        return id;
    }

    public JsonObject getRecord() {
        return record;
    }

    public void setRecord(JsonObject record) {
        this.record = record;
    }
}
