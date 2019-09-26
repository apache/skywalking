package org.apache.skywalking.plugin.test.mockcollector.entity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

/**
 * Created by xin on 2017/7/14.
 */
public class ValidateDataSerializer implements JsonSerializer<ValidateData> {
    @Override
    public JsonElement serialize(ValidateData src, Type typeOfSrc, JsonSerializationContext context) {
        Gson gson = new GsonBuilder().registerTypeAdapter(RegistryItem.class, new RegistryItemSerializer())
            .registerTypeAdapter(SegmentItems.class, new SegmentItemsSerializer()).create();

        JsonObject jsonObject = new JsonObject();
        jsonObject.add("registryItems", gson.toJsonTree(src.getRegistryItem()));
        jsonObject.add("segmentItems", gson.toJsonTree(src.getSegmentItem()));
        return jsonObject;
    }
}
