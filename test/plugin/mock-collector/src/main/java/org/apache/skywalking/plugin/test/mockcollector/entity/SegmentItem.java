package org.apache.skywalking.plugin.test.mockcollector.entity;

import java.util.ArrayList;
import java.util.List;

public class SegmentItem {
    private String applicationCode;
    private List<Segment> segments;

    public SegmentItem(String applicationCode) {
        this.applicationCode = applicationCode;
        segments = new ArrayList<>();
    }

    public void addSegments(Segment item) {
        segments.add(item);
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public List<Segment> getSegments() {
        return segments;
    }
}
