package org.skywalking.apm.collector.worker.segment.entity;

/**
 * @author pengys5
 */
public class SegmentAndJson {

    private final Segment segment;
    private final String jsonStr;

    public SegmentAndJson(Segment segment, String jsonStr) {
        this.segment = segment;
        this.jsonStr = jsonStr;
    }

    public Segment getSegment() {
        return segment;
    }

    public String getJsonStr() {
        return jsonStr;
    }
}
