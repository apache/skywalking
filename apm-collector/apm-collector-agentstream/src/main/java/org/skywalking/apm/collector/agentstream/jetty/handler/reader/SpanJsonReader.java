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

    private static final String SPAN_ID = "si";
    private static final String SPAN_TYPE_VALUE = "tv";
    private static final String SPAN_LAYER_VALUE = "lv";
    private static final String PARENT_SPAN_ID = "ps";
    private static final String START_TIME = "st";
    private static final String END_TIME = "et";
    private static final String COMPONENT_ID = "ci";
    private static final String COMPONENT_NAME = "cn";
    private static final String OPERATION_NAME_ID = "oi";
    private static final String OPERATION_NAME = "on";
    private static final String PEER_ID = "pi";
    private static final String PEER = "pn";
    private static final String IS_ERROR = "ie";
    private static final String TAGS = "to";
    private static final String LOGS = "lo";

    @Override public SpanObject read(JsonReader reader) throws IOException {
        SpanObject.Builder builder = SpanObject.newBuilder();

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case SPAN_ID:
                    builder.setSpanId(reader.nextInt());
                    break;
                case SPAN_TYPE_VALUE:
                    builder.setSpanTypeValue(reader.nextInt());
                    break;
                case SPAN_LAYER_VALUE:
                    builder.setSpanLayerValue(reader.nextInt());
                    break;
                case PARENT_SPAN_ID:
                    builder.setParentSpanId(reader.nextInt());
                    break;
                case START_TIME:
                    builder.setStartTime(reader.nextLong());
                    break;
                case END_TIME:
                    builder.setEndTime(reader.nextLong());
                    break;
                case COMPONENT_ID:
                    builder.setComponentId(reader.nextInt());
                    break;
                case COMPONENT_NAME:
                    builder.setComponent(reader.nextString());
                    break;
                case OPERATION_NAME_ID:
                    builder.setOperationNameId(reader.nextInt());
                    break;
                case OPERATION_NAME:
                    builder.setOperationName(reader.nextString());
                    break;
                case PEER_ID:
                    builder.setPeerId(reader.nextInt());
                    break;
                case PEER:
                    builder.setPeer(reader.nextString());
                    break;
                case IS_ERROR:
                    builder.setIsError(reader.nextBoolean());
                    break;
                case TAGS:
                    reader.beginArray();
                    while (reader.hasNext()) {
                        builder.addTags(keyWithStringValueJsonReader.read(reader));
                    }
                    reader.endArray();
                    break;
                case LOGS:
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
