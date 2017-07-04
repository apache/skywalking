package org.skywalking.apm.collector.worker.segment.entity;

import com.google.gson.JsonObject;
import org.skywalking.apm.collector.worker.segment.SegmentIndex;
import org.skywalking.apm.network.proto.TraceSegmentObject;

/**
 * @author pengys5
 */
public class SegmentAndBase64 {

    private final TraceSegmentObject object;
    private final String base64;

    public SegmentAndBase64(TraceSegmentObject object, String base64) {
        this.object = object;
        this.base64 = base64;
    }

    public TraceSegmentObject getObject() {
        return object;
    }

    public String getBase64() {
        return base64;
    }

    public String getSegmentJsonStr() {
        JsonObject segmentJson = new JsonObject();
        segmentJson.addProperty(SegmentIndex.TRACE_SEGMENT_ID, object.getTraceSegmentId());
        segmentJson.addProperty(SegmentIndex.SEGMENT_OBJ_BLOB, base64);
        return segmentJson.toString();
    }
}
