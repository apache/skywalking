package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

import java.util.ArrayList;
import java.util.List;

public class SegmentItemForRead implements SegmentItem {
    private String applicationCode;
    private String segmentSize;
    private List<SegmentForRead> segments;

    public List<SegmentForRead> getSegments() {
        return segments;
    }

    public void setSegments(List<SegmentForRead> segments) {
        this.segments = segments;
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public void setApplicationCode(String applicationCode) {
        this.applicationCode = applicationCode;
    }

    public String getSegmentSize() {
        return segmentSize;
    }

    public void setSegmentSize(String segmentSize) {
        this.segmentSize = segmentSize;
    }

    @Override
    public String applicationCode() {
        return applicationCode;
    }

    @Override
    public String segmentSize() {
        return segmentSize;
    }

    @Override
    public List<Segment> segments() {
        if (segments == null) {
            return null;
        }
        return new ArrayList<>(segments);
    }

    @Override public String toString() {
        StringBuilder message = new StringBuilder(String.format("\nSegment Item[%s]", applicationCode));
        message.append(String.format(" - segment size:\t\t%s\n", segmentSize));
        return message.toString();
    }
}
