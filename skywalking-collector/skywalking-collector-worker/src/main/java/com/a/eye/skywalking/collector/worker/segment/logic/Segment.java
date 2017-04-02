package com.a.eye.skywalking.collector.worker.segment.logic;

import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceId.DistributedTraceId;
import com.a.eye.skywalking.trace.TraceId.DistributedTraceIds;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author pengys5
 */
public class Segment {

    /**
     * The id of this trace segment.
     * Every segment has its unique-global-id.
     */
    @Expose
    @SerializedName(value = "ts")
    private String traceSegmentId;

    /**
     * The start time of this trace segment.
     */
    @Expose
    @SerializedName(value = "st")
    private long startTime;

    /**
     * The end time of this trace segment.
     */
    @Expose
    @SerializedName(value = "et")
    private long endTime;

    /**
     * The refs of parent trace segments, except the primary one.
     * For most RPC call, {@link #refs} contains only one element,
     * but if this segment is a start span of batch process, the segment faces multi parents,
     * at this moment, we use this {@link #refs} to link them.
     */
    @Expose
    @SerializedName(value = "rs")
    private List<TraceSegmentRef> refs;

    /**
     * The spans belong to this trace segment.
     * They all have finished.
     * All active spans are hold and controlled by "skywalking-api" module.
     */
    @Expose
    @SerializedName(value = "ss")
    private List<Span> spans;

    /**
     * The <code>applicationCode</code> represents a name of current application/JVM and indicates which is business
     * role in the cluster.
     * <p>
     * e.g. account_app, billing_app
     */
    @Expose
    @SerializedName(value = "ac")
    private String applicationCode;

    /**
     * The <code>relatedGlobalTraces</code> represent a set of all related trace. Most time it contains only one
     * element, because only one parent {@link TraceSegment} exists, but, in batch scenario, the num becomes greater
     * than 1, also meaning multi-parents {@link TraceSegment}.
     * <p>
     * The difference between <code>relatedGlobalTraces</code> and {@link #refs} is:
     * {@link #refs} targets this {@link TraceSegment}'s direct parent,
     * <p>
     * and
     * <p>
     * <code>relatedGlobalTraces</code> targets this {@link TraceSegment}'s related call chain, a call chain contains
     * multi {@link TraceSegment}s, only using {@link #refs} is not enough for analysis and ui.
     */
    @Expose
    @SerializedName(value = "gt")
    private DistributedTraceIds relatedGlobalTraces;

    /**
     * Establish the link between this segment and its parents.
     *
     * @param refSegment {@link TraceSegmentRef}
     */
    public void ref(TraceSegmentRef refSegment) {
        if (refs == null) {
            refs = new LinkedList<TraceSegmentRef>();
        }
        if (!refs.contains(refSegment)) {
            refs.add(refSegment);
        }
    }

    public void relatedGlobalTraces(List<DistributedTraceId> distributedTraceIds) {
        if (distributedTraceIds == null || distributedTraceIds.size() == 0) {
            return;
        }
        for (DistributedTraceId distributedTraceId : distributedTraceIds) {
            relatedGlobalTraces.append(distributedTraceId);
        }
    }

    public String getTraceSegmentId() {
        return traceSegmentId;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public List<TraceSegmentRef> getRefs() {
        if (refs == null) {
            return null;
        }
        return Collections.unmodifiableList(refs);
    }

    public List<DistributedTraceId> getRelatedGlobalTraces() {
        return relatedGlobalTraces.getRelatedGlobalTraces();
    }

    public List<Span> getSpans() {
        return Collections.unmodifiableList(spans);
    }

    public String getApplicationCode() {
        return applicationCode;
    }
}
