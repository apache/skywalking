package com.a.eye.skywalking.collector.worker.segment.logic;

import com.google.gson.Gson;

/**
 * @author pengys5
 */
public enum SegmentDeserialize {
    INSTANCE;

    private Gson gson = new Gson();

    public Segment deserializeFromES(String segmentSource) {
        Segment segment = gson.fromJson(segmentSource, Segment.class);
        return segment;
    }
}
