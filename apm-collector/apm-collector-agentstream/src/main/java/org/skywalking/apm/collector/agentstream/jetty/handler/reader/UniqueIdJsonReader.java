package org.skywalking.apm.collector.agentstream.jetty.handler.reader;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import org.skywalking.apm.network.proto.UniqueId;

/**
 * @author pengys5
 */
public class UniqueIdJsonReader implements StreamJsonReader<UniqueId> {

    @Override public UniqueId read(JsonReader reader) throws IOException {
        UniqueId.Builder builder = UniqueId.newBuilder();

        reader.beginArray();
        while (reader.hasNext()) {
            builder.addIdParts(reader.nextLong());
        }
        reader.endArray();
        return builder.build();
    }
}
