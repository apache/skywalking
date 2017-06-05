package org.skywalking.apm.collector.worker.instance.entity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;
import org.skywalking.apm.collector.worker.storage.Data;

public class HeartBeat  implements Data {

    private String instanceId;
    private long timestamp;

    public HeartBeat(String instanceId) {
        this.instanceId = instanceId;
    }

    public HeartBeat(JsonObject jsonObject) {
        this.instanceId = jsonObject.get("ii").getAsString();
        this.timestamp = jsonObject.get("st").getAsLong();
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String getId() {
        return instanceId;
    }

    @Override
    public void merge(Map<String, ?> dbData) {
    }

    public void merge(HeartBeat heartBeat) {
        if (timestamp > heartBeat.getTimestamp()) {
            return;
        }

        timestamp = heartBeat.getTimestamp();
    }

    public String asMap() {
        JsonObject object = new JsonObject();
        object.addProperty("pt", timestamp);
        return new Gson().toJson(object);
    }
}
