package com.a.eye.skywalking.plugin.tomcat78x;

import com.a.eye.skywalking.context.TracerContextListener;
import com.a.eye.skywalking.trace.TraceSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhangxin
 */
public enum TestTraceContextListener implements TracerContextListener {
    INSTANCE;

    private List<TraceSegment> traceSegments;

    TestTraceContextListener() {
        this.traceSegments = new ArrayList<TraceSegment>();
    }

    @Override
    public void afterFinished(TraceSegment traceSegment) {
        this.traceSegments.add(traceSegment);
    }

    public List<TraceSegment> getTraceSegments() {
        return traceSegments;
    }

    public void clearData() {
        this.traceSegments.clear();
    }
}
