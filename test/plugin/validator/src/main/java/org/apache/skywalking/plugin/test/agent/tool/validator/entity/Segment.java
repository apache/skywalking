package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

import java.util.List;

public interface Segment {
    String segmentId();

    List<Span> spans();

    void setSegmentId(String segmentId);
}
