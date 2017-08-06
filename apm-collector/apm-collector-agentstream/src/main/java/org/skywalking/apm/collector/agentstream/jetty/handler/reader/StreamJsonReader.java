package org.skywalking.apm.collector.agentstream.jetty.handler.reader;

import com.google.gson.stream.JsonReader;
import java.io.IOException;

/**
 * @author pengys5
 */
public interface StreamJsonReader<T> {
    T read(JsonReader reader) throws IOException;
}
