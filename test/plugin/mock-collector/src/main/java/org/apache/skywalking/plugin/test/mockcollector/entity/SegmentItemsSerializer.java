package org.apache.skywalking.plugin.test.mockcollector.entity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class SegmentItemsSerializer implements JsonSerializer<SegmentItems> {

    @Override
    public JsonElement serialize(SegmentItems src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray applicationSegmentItems = new JsonArray();
        src.getSegmentItems().forEach((applicationCode, segmentItem) -> {
            JsonObject segmentJson = new JsonObject();
            segmentJson.addProperty("applicationCode", applicationCode);
            segmentJson.addProperty("segmentSize", segmentItem.getSegments().size());
            JsonArray segments = new JsonArray();
            segmentItem.getSegments().forEach(segment -> {
                segments.add(new Gson().toJsonTree(segment));
            });
            segmentJson.add("segments", segments);
            applicationSegmentItems.add(segmentJson);
        });

        return applicationSegmentItems;
    }
}
