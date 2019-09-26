package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

import java.util.List;

public interface Span {
    String operationName();

    String operationId();

    String parentSpanId();

    String spanId();

    String spanLayer();

    List<KeyValuePair> tags();

    List<LogEvent> logs();

    String startTime();

    String endTime();

    String componentId();

    String componentName();

    boolean error();

    String spanType();

    String peer();

    String peerId();

    List<SegmentRef> refs();

    void setActualRefs(List<SegmentRef> refs);

    List<SegmentRef> actualRefs();
}
