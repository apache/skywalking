package org.skywalking.apm.collector.worker.segment.mock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.UpstreamSegment;

/**
 * @author pengys5
 */
public enum UpstreamSegmentFromJson {
    INSTANCE;

    private static final String globalTraceIds = "globalTraceIds";
    private static final String segment = "segment";

    public UpstreamSegment build(JsonObject upstreamJsonObj) {
        UpstreamSegment.Builder builder = UpstreamSegment.newBuilder();
        buildUpStream(builder, upstreamJsonObj);
        return builder.build();
    }

    private void buildUpStream(UpstreamSegment.Builder builder, JsonObject upstreamJsonObj) {
        if (upstreamJsonObj.has(globalTraceIds)) {
            JsonArray globalTraceIdArray = upstreamJsonObj.get(globalTraceIds).getAsJsonArray();
            globalTraceIdArray.forEach(globalTraceIdElement -> {
                builder.addGlobalTraceIds(globalTraceIdElement.getAsString());
            });
        }

        TraceSegmentObject segmentObject = SegmentFromJson.INSTANCE.build(upstreamJsonObj.get(segment).getAsJsonObject());
        builder.setSegment(segmentObject.toByteString());
    }
}
