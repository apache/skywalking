package org.skywalking.apm.collector.agentstream.jetty.handler.reader;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import org.skywalking.apm.network.proto.TraceSegmentReference;

/**
 * @author pengys5
 */
public class ReferenceJsonReader implements StreamJsonReader<TraceSegmentReference> {

    private UniqueIdJsonReader uniqueIdJsonReader = new UniqueIdJsonReader();

    private static final String TS = "ts";
    private static final String AI = "ai";
    private static final String SI = "si";
    private static final String VI = "vi";
    private static final String VN = "vn";
    private static final String NI = "ni";
    private static final String NN = "nn";
    private static final String EA = "ea";
    private static final String EI = "ei";
    private static final String EN = "en";
    private static final String RV = "rv";

    @Override public TraceSegmentReference read(JsonReader reader) throws IOException {
        TraceSegmentReference.Builder builder = TraceSegmentReference.newBuilder();

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case TS:
                    builder.setParentTraceSegmentId(uniqueIdJsonReader.read(reader));
                    break;
                case AI:
                    builder.setParentApplicationInstanceId(reader.nextInt());
                    break;
                case SI:
                    builder.setParentSpanId(reader.nextInt());
                    break;
                case VI:
                    builder.setParentServiceId(reader.nextInt());
                    break;
                case VN:
                    builder.setParentServiceName(reader.nextString());
                    break;
                case NI:
                    builder.setNetworkAddressId(reader.nextInt());
                    break;
                case NN:
                    builder.setNetworkAddress(reader.nextString());
                    break;
                case EA:
                    builder.setEntryApplicationInstanceId(reader.nextInt());
                    break;
                case EI:
                    builder.setEntryServiceId(reader.nextInt());
                    break;
                case EN:
                    builder.setEntryServiceName(reader.nextString());
                    break;
                case RV:
                    builder.setRefTypeValue(reader.nextInt());
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
