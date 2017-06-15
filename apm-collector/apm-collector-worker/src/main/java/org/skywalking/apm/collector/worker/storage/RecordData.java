package org.skywalking.apm.collector.worker.storage;

import com.google.gson.JsonObject;
import org.skywalking.apm.collector.actor.selector.AbstractHashMessage;
import org.skywalking.apm.collector.worker.Const;

import java.util.Map;

/**
 * @author pengys5
 */
public class RecordData extends AbstractHashMessage implements Data {

    private String id;
    private String aggId;
    private JsonObject data;

    public RecordData(String id) {
        super(id);
        this.id = id;
        String[] ids = this.id.split(Const.IDS_SPLIT);
        for (int i = 1; i < ids.length; i++) {
            if (i == 1) {
                this.aggId = ids[i];
            } else {
                this.aggId += Const.ID_SPLIT + ids[i];
            }
        }
        data = new JsonObject();
    }

    public String getId() {
        return id;
    }

    public JsonObject get() {
        data.addProperty(AbstractIndex.AGG_COLUMN, this.aggId);
        return data;
    }

    public void set(JsonObject record) {
        this.data = record;
    }

    @Override
    public void merge(Map<String, ?> source) {
        //TODO
    }
}
