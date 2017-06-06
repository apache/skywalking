package org.skywalking.apm.collector.worker.segment.entity;

import com.google.gson.annotations.SerializedName;

/**
 * @author pengys5
 */
public class TraceSegmentRef {

    @SerializedName("ts")
    private String traceSegmentId;

    @SerializedName("si")
    private int spanId = -1;

    @SerializedName("ac")
    private String applicationCode;

    @SerializedName("ph")
    private String peerHost;

    public String getTraceSegmentId() {
        return traceSegmentId;
    }

    public int getSpanId() {
        return spanId;
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public String getPeerHost() {
        return peerHost;
    }
}
