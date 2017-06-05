package org.skywalking.apm.collector.worker.tools;

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
}
