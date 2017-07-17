package org.skywalking.apm.agent.core.test.tools;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

public class SegmentStorage {
    private LinkedList<TraceSegment> traceSegments;
    private LinkedList<IgnoredTracerContext> ignoredTracerContexts;

   public SegmentStorage() {
        traceSegments = new LinkedList<TraceSegment>();
        ignoredTracerContexts = new LinkedList<IgnoredTracerContext>();
    }

    void addTraceSegment(TraceSegment segment) {
        traceSegments.add(segment);
    }

    public List<TraceSegment> getTraceSegments() {
        return traceSegments;
    }

    void addIgnoreTraceContext(IgnoredTracerContext context) {
        this.ignoredTracerContexts.add(context);
    }

    public LinkedList<IgnoredTracerContext> getIgnoredTracerContexts() {
        return ignoredTracerContexts;
    }
}
