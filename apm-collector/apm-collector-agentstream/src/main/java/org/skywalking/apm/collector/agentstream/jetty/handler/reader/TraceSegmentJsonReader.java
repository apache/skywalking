package org.skywalking.apm.collector.agentstream.jetty.handler.reader;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class TraceSegmentJsonReader implements StreamJsonReader<TraceSegment> {

    private final Logger logger = LoggerFactory.getLogger(TraceSegmentJsonReader.class);

    private UniqueIdJsonReader uniqueIdJsonReader = new UniqueIdJsonReader();
    private SegmentJsonReader segmentJsonReader = new SegmentJsonReader();

    private static final String GT = "gt";
    private static final String SG = "sg";

    @Override public TraceSegment read(JsonReader reader) throws IOException {
        TraceSegment traceSegment = new TraceSegment();

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case GT:
                    reader.beginArray();
                    while (reader.hasNext()) {
                        traceSegment.addGlobalTraceId(uniqueIdJsonReader.read(reader));
                    }
                    reader.endArray();

                    if (logger.isDebugEnabled()) {
                        traceSegment.getGlobalTraceIds().forEach(uniqueId -> {
                            StringBuilder globalTraceId = new StringBuilder();
                            uniqueId.getIdPartsList().forEach(idPart -> globalTraceId.append(idPart));
                            logger.debug("global trace id: {}", globalTraceId.toString());
                        });
                    }
                    break;
                case SG:
                    traceSegment.setTraceSegmentObject(segmentJsonReader.read(reader));
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        return traceSegment;
    }
}
