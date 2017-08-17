package org.skywalking.apm.collector.agentstream.mock;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.FileNotFoundException;
import java.io.FileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public enum JsonFileReader {
    INSTANCE;

    private final Logger logger = LoggerFactory.getLogger(JsonFileReader.class);

    public JsonElement read(String fileName) throws FileNotFoundException {
        String path = this.getClass().getClassLoader().getResource(fileName).getFile();
        logger.debug("path: {}", path);
        JsonParser jsonParser = new JsonParser();
        return jsonParser.parse(new FileReader(path));
    }
}
