package org.skywalking.apm.collector.agentstream.jetty.handler.reader;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import org.skywalking.apm.network.proto.LogMessage;

/**
 * @author pengys5
 */
public class LogJsonReader implements StreamJsonReader<LogMessage> {

    private KeyWithStringValueJsonReader keyWithStringValueJsonReader = new KeyWithStringValueJsonReader();

    private static final String TI = "ti";
    private static final String LD = "ld";

    @Override public LogMessage read(JsonReader reader) throws IOException {
        LogMessage.Builder builder = LogMessage.newBuilder();

        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case TI:
                    builder.setTime(reader.nextLong());
                case LD:
                    reader.beginArray();
                    while (reader.hasNext()) {
                        builder.addData(keyWithStringValueJsonReader.read(reader));
                    }
                    reader.endArray();
                default:
                    reader.skipValue();
            }
        }

        return builder.build();
    }
}
