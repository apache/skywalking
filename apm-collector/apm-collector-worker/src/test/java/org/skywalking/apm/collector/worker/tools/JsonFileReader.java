package org.skywalking.apm.collector.worker.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.FileNotFoundException;
import java.io.FileReader;

/**
 * @author pengys5
 */
public enum JsonFileReader {
    INSTANCE;

    public String read(String path) throws FileNotFoundException {
        JsonParser jsonParser = new JsonParser();
        JsonElement jsonElement = jsonParser.parse(new FileReader(path));
        return jsonElement.toString();
    }

    public JsonElement parse(String path) throws FileNotFoundException {
        JsonParser jsonParser = new JsonParser();
        return jsonParser.parse(new FileReader(path));
    }

    public String readSegment(String path) throws FileNotFoundException {
        JsonParser jsonParser = new JsonParser();
        JsonElement jsonElement = jsonParser.parse(new FileReader(path));

        StringBuilder segmentBuilder = new StringBuilder();
        JsonArray segments = jsonElement.getAsJsonArray();
        for (int i = 0; i < segments.size(); i++) {
            JsonElement segment = segments.get(i);
            String segmentStr = segment.toString();
            segmentBuilder.append(segmentStr.length()).append(" ").append(segmentStr);
        }

        return segmentBuilder.toString();
    }
}
