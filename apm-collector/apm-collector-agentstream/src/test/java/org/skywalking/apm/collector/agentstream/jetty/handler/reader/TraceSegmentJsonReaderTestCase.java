package org.skywalking.apm.collector.agentstream.jetty.handler.reader;

import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;
import org.skywalking.apm.collector.agentstream.mock.JsonFileReader;

/**
 * @author pengys5
 */
public class TraceSegmentJsonReaderTestCase {

    @Test
    public void testRead() throws IOException {
        TraceSegmentJsonReader reader = new TraceSegmentJsonReader();
        JsonElement jsonElement = JsonFileReader.INSTANCE.read("json/segment/normal/dubbox-consumer.json");
        System.out.println(jsonElement.toString());

        JsonReader jsonReader = new JsonReader(new StringReader(jsonElement.toString()));
        jsonReader.beginArray();
        while (jsonReader.hasNext()) {
            reader.read(jsonReader);
        }
        jsonReader.endArray();
    }
}
