package org.skywalking.apm.trace;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.skywalking.apm.trace.tag.Tags;

/**
 * {@link TraceSegmentRef} is like a pointer, which ref to another {@link TraceSegment},
 * use {@link #spanId} point to the exact span of the ref {@link TraceSegment}.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public class TraceSegmentRef {
    /**
     * {@link TraceSegment#traceSegmentId}
     */
    @Expose
    @SerializedName(value = "ts")
    private String traceSegmentId;

    /**
     * {@link Span#spanId}
     */
    @Expose
    @SerializedName(value = "si")
    private int spanId = -1;

    /**
     * {@link TraceSegment#applicationCode}
     */
    @Expose
    @SerializedName(value = "ac")
    private String applicationCode;

    /**
     * {@link Tags#PEER_HOST}
     */
    @Expose
    @SerializedName(value = "ph")
    private String peerHost;

    /**
     * Create a {@link TraceSegmentRef} instance, without any data.
     */
    public TraceSegmentRef() {
    }

    public String getTraceSegmentId() {
        return traceSegmentId;
    }

    public void setTraceSegmentId(String traceSegmentId) {
        this.traceSegmentId = traceSegmentId;
    }

    public int getSpanId() {
        return spanId;
    }

    public void setSpanId(int spanId) {
        this.spanId = spanId;
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public void setApplicationCode(String applicationCode) {
        this.applicationCode = applicationCode;
    }

    public String getPeerHost() {
        return peerHost;
    }

    public void setPeerHost(String peerHost) {
        this.peerHost = peerHost;
    }

    @Override
    public String toString() {
        return "TraceSegmentRef{" +
            "traceSegmentId='" + traceSegmentId + '\'' +
            ", spanId=" + spanId +
            ", applicationCode='" + applicationCode + '\'' +
            ", peerHost='" + peerHost + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        TraceSegmentRef that = (TraceSegmentRef) o;

        return traceSegmentId != null ? traceSegmentId.equals(that.traceSegmentId) : that.traceSegmentId == null;
    }

    @Override
    public int hashCode() {
        return traceSegmentId != null ? traceSegmentId.hashCode() : 0;
    }
}
