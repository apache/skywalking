package org.skywalking.apm.collector.worker.segment.mock;

import com.google.gson.JsonObject;
import java.util.List;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.TraceSegmentReference;

/**
 * @author pengys5
 */
public enum SegmentFromJson {
    INSTANCE;

    private static final String traceSegmentId = "traceSegmentId";
    private static final String refs = "refs";
    private static final String spans = "spans";
    private static final String applicationId = "applicationId";
    private static final String applicationInstanceId = "applicationInstanceId";

    public TraceSegmentObject build(JsonObject segmentJsonObj) {
        TraceSegmentObject.Builder builder = TraceSegmentObject.newBuilder();
        buildSegment(builder, segmentJsonObj);
        return builder.build();
    }

    private void buildSegment(TraceSegmentObject.Builder builder, JsonObject segmentJsonObj) {
        builder.setTraceSegmentId(segmentJsonObj.get(traceSegmentId).getAsString());
        builder.setApplicationId(segmentJsonObj.get(applicationId).getAsInt());
        builder.setApplicationInstanceId(segmentJsonObj.get(applicationInstanceId).getAsInt());

        if (segmentJsonObj.has(refs)) {
            List<TraceSegmentReference.Builder> refBuilders = ReferencesFromJson.INSTANCE.build(segmentJsonObj.get(refs).getAsJsonArray());
            refBuilders.forEach(refBuilder -> builder.addRefs(refBuilder));
        }

        List<SpanObject.Builder> spanBuilders = SpanFromJson.INSTANCE.build(segmentJsonObj.get(spans).getAsJsonArray());
        spanBuilders.forEach(spanBuilder -> builder.addSpans(spanBuilder));
    }
}