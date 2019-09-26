package org.apache.skywalking.plugin.test.mockcollector.entity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class RegistryItemSerializer implements JsonSerializer<RegistryItem> {
    @Override
    public JsonElement serialize(RegistryItem src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        JsonArray applicationArrays = new JsonArray();
        src.getApplications().forEach((applicationCode, applicationId) -> {
            JsonObject applicationJson = new JsonObject();
            applicationJson.addProperty(applicationCode, applicationId);
            applicationArrays.add(applicationJson);
        });
        jsonObject.add("applications", applicationArrays);

        JsonArray instanceArrays = new JsonArray();
        src.getInstanceMapping().forEach((applicationCode, instanceIds) -> {
            JsonObject instanceJson = new JsonObject();
            instanceJson.addProperty(applicationCode, instanceIds.size());
            instanceArrays.add(instanceJson);
        });
        jsonObject.add("instances", instanceArrays);

        JsonArray operationNameArrays = new JsonArray();
        src.getOperationNames().forEach((applicationCode, operationNames) -> {
            JsonObject instanceJson = new JsonObject();
            instanceJson.add(applicationCode, new Gson().toJsonTree(operationNames));
            operationNameArrays.add(instanceJson);
        });
        jsonObject.add("operationNames", operationNameArrays);

        JsonArray heartBeatArrays = new JsonArray();
        src.getHeartBeats().forEach((applicationCode, count) -> {
            JsonObject instanceJson = new JsonObject();
            instanceJson.addProperty(applicationCode, count);
            heartBeatArrays.add(instanceJson);
        });
        jsonObject.add("heartbeat", heartBeatArrays);
        return jsonObject;
    }
}
