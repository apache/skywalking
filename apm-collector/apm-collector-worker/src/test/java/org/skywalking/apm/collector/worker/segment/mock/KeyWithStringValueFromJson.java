package org.skywalking.apm.collector.worker.segment.mock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.network.proto.KeyWithStringValue;

/**
 * @author pengys5
 */
public enum KeyWithStringValueFromJson {
    INSTANCE;

    private static final String key = "key";
    private static final String value = "value";

    public List<KeyWithStringValue.Builder> build(JsonArray kvArray) {
        List<KeyWithStringValue.Builder> kv = new LinkedList<>();
        for (int i = 0; i < kvArray.size(); i++) {
            JsonObject tagJson = kvArray.get(i).getAsJsonObject();
            KeyWithStringValue.Builder builder = KeyWithStringValue.newBuilder();
            buildKV(builder, tagJson);
            kv.add(builder);
        }
        return kv;
    }

    private void buildKV(KeyWithStringValue.Builder builder, JsonObject tagJson) {
        builder.setKey(tagJson.get(key).getAsString());
        builder.setValue(tagJson.get(value).getAsString());
    }
}
