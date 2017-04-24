package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.selector.AbstractHashMessage;
import com.a.eye.skywalking.collector.worker.Const;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * @author pengys5
 */
public class RecordData extends AbstractHashMessage implements Data {

    private String id;
    private String aggId;
    private JsonObject record;

    public RecordData(String key) {
        super(key);
        this.id = key;
        String[] ids = id.split(Const.IDS_SPLIT);
        for (int i = 1; i < ids.length; i++) {
            if (i == 1) {
                this.aggId = ids[i];
            } else {
                this.aggId += Const.ID_SPLIT + ids[i];
            }
        }
        record = new JsonObject();
    }

    public String getId() {
        return id;
    }

    public JsonObject getRecord() {
        record.addProperty(AbstractIndex.AGG_COLUMN, this.aggId);
        return record;
    }

    public void setRecord(JsonObject record) {
        this.record = record;
    }

    @Override
    public void merge(Map<String, ?> dbData) {
    }
}
