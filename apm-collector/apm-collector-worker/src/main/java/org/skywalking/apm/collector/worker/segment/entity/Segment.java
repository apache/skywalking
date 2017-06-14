package org.skywalking.apm.collector.worker.segment.entity;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * @author pengys5
 */
public class Segment {

    @SerializedName("ts")
    private String traceSegmentId;

    @SerializedName("st")
    private long startTime;

    @SerializedName("et")
    private long endTime;

    @SerializedName("rs")
    private List<TraceSegmentRef> refs;

    @SerializedName("ss")
    private List<Span> spans;

    @SerializedName("ac")
    private String applicationCode;

    @SerializedName("gt")
    private GlobalTraceId relatedGlobalTraces;

    public String getTraceSegmentId() {
        return traceSegmentId;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public List<TraceSegmentRef> getRefs() {
        return refs;
    }

    public List<Span> getSpans() {
        return spans;
    }

    public GlobalTraceId getRelatedGlobalTraces() {
        return relatedGlobalTraces;
    }
}
