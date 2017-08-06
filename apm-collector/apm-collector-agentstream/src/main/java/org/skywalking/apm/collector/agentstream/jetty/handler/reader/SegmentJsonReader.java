package org.skywalking.apm.collector.agentstream.jetty.handler.reader;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class SegmentJsonReader implements StreamJsonReader<TraceSegmentObject> {

    private final Logger logger = LoggerFactory.getLogger(SegmentJsonReader.class);

    private UniqueIdJsonReader uniqueIdJsonReader = new UniqueIdJsonReader();
    private ReferenceJsonReader referenceJsonReader = new ReferenceJsonReader();
    private SpanJsonReader spanJsonReader = new SpanJsonReader();

    private static final String TS = "ts";
    private static final String AI = "ai";
    private static final String II = "ii";
    private static final String RS = "rs";
    private static final String SS = "ss";

    @Override public TraceSegmentObject read(JsonReader reader) throws IOException {
        TraceSegmentObject.Builder builder = TraceSegmentObject.newBuilder();

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case TS:
                    builder.setTraceSegmentId(uniqueIdJsonReader.read(reader));
                    if (logger.isDebugEnabled()) {
                        StringBuilder segmentId = new StringBuilder();
                        builder.getTraceSegmentId().getIdPartsList().forEach(idPart -> segmentId.append(idPart));
                        logger.debug("segment id: {}", segmentId);
                    }
                    break;
                case AI:
                    builder.setApplicationId(reader.nextInt());
                    break;
                case II:
                    builder.setApplicationInstanceId(reader.nextInt());
                    break;
                case RS:
                    reader.beginArray();
                    while (reader.hasNext()) {
                        builder.addRefs(referenceJsonReader.read(reader));
                    }
                    reader.endArray();
                    break;
                case SS:
                    reader.beginArray();
                    while (reader.hasNext()) {
                        builder.addSpans(spanJsonReader.read(reader));
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
