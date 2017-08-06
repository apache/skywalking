package org.skywalking.apm.collector.agentstream.jetty.handler.reader;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import org.skywalking.apm.network.proto.SpanObject;

/**
 * @author pengys5
 */
public class SpanJsonReader implements StreamJsonReader<SpanObject> {

    private KeyWithStringValueJsonReader keyWithStringValueJsonReader = new KeyWithStringValueJsonReader();
    private LogJsonReader logJsonReader = new LogJsonReader();

    private static final String SI = "si";
    private static final String TV = "tv";
    private static final String LV = "lv";
    private static final String PS = "ps";
    private static final String ST = "st";
    private static final String ET = "et";
    private static final String CI = "ci";
    private static final String CN = "cn";
    private static final String OI = "oi";
    private static final String ON = "on";
    private static final String PI = "pi";
    private static final String PN = "pn";
    private static final String IE = "ie";
    private static final String TO = "to";
    private static final String LO = "lo";

    @Override public SpanObject read(JsonReader reader) throws IOException {
        SpanObject.Builder builder = SpanObject.newBuilder();

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case SI:
                    builder.setSpanId(reader.nextInt());
                    break;
                case TV:
                    builder.setSpanTypeValue(reader.nextInt());
                    break;
                case LV:
                    builder.setSpanLayerValue(reader.nextInt());
                    break;
                case PS:
                    builder.setParentSpanId(reader.nextInt());
                    break;
                case ST:
                    builder.setStartTime(reader.nextLong());
                    break;
                case ET:
                    builder.setEndTime(reader.nextLong());
                    break;
                case CI:
                    builder.setComponentId(reader.nextInt());
                    break;
                case CN:
                    builder.setComponent(reader.nextString());
                    break;
                case OI:
                    builder.setOperationNameId(reader.nextInt());
                    break;
                case ON:
                    builder.setOperationName(reader.nextString());
                    break;
                case PI:
                    builder.setPeerId(reader.nextInt());
                    break;
                case PN:
                    builder.setPeer(reader.nextString());
                    break;
                case IE:
                    builder.setIsError(reader.nextBoolean());
                    break;
                case TO:
                    reader.beginArray();
                    while (reader.hasNext()) {
                        builder.addTags(keyWithStringValueJsonReader.read(reader));
                    }
                    reader.endArray();
                    break;
                case LO:
                    reader.beginArray();
                    while (reader.hasNext()) {
                        builder.addLogs(logJsonReader.read(reader));
                    }
                    reader.endArray();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        return builder.build();
    }
}
