package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.selector.AbstractHashMessage;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.storage.index.AbstractIndex;
import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public class RecordData extends AbstractHashMessage {

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
                this.aggId = this.aggId + Const.ID_SPLIT + ids[i];
            }
        }
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
}
