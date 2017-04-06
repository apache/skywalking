package com.a.eye.skywalking.trace;

import com.a.eye.skywalking.trace.TraceId.DistributedTraceId;
import com.a.eye.skywalking.trace.TraceId.DistributedTraceIds;
import com.a.eye.skywalking.trace.TraceId.NewDistributedTraceId;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * {@link TraceSegment} is a segment or fragment of the distributed trace.
 * {@see https://github.com/opentracing/specification/blob/master/specification.md#the-opentracing-data-model}
 * A {@link
 * TraceSegment} means the segment, which exists in current {@link Thread}. And the distributed trace is formed by multi
 * {@link TraceSegment}s, because the distributed trace crosses multi-processes, multi-threads.
 *
 * Created by wusheng on 2017/2/17.
 */
public class TraceSegment {
    private static final String ID_TYPE = "Segment";

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
     *
     * e.g. account_app, billing_app
     */
    @Expose
    @SerializedName(value = "ac")
    private String applicationCode;

    /**
     * The <code>relatedGlobalTraces</code> represent a set of all related trace. Most time it contains only one
     * element, because only one parent {@link TraceSegment} exists, but, in batch scenario, the num becomes greater
     * than 1, also meaning multi-parents {@link TraceSegment}.
     *
     * The difference between <code>relatedGlobalTraces</code> and {@link #refs} is:
     * {@link #refs} targets this {@link TraceSegment}'s direct parent,
     *
     * and
     *
     * <code>relatedGlobalTraces</code> targets this {@link TraceSegment}'s related call chain, a call chain contains
     * multi {@link TraceSegment}s, only using {@link #refs} is not enough for analysis and ui.
     */
    @Expose
    @SerializedName(value = "gt")
    private DistributedTraceIds relatedGlobalTraces;

    /**
     * The <code>sampled</code> is a flag, which represent, when this {@link TraceSegment} finished, it need to be send
     * to Collector.
     *
     * Its value depends on SamplingService. True, by default.
     *
     * This value is not serialized.
     */
    private boolean sampled;

    /**
     * Create a trace segment, by the given applicationCode.
     */
    public TraceSegment(String applicationCode) {
        this();
        this.applicationCode = applicationCode;
    }

    /**
     * Create a default/empty trace segment,
     * with current time as start time,
     * and generate a new segment id.
     */
    public TraceSegment() {
        this.startTime = System.currentTimeMillis();
        this.traceSegmentId = GlobalIdGenerator.generate(ID_TYPE);
        this.spans = new LinkedList<Span>();
        this.relatedGlobalTraces = new DistributedTraceIds();
        this.relatedGlobalTraces.append(new NewDistributedTraceId());
        this.sampled = true;
    }

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

    /**
     * Establish the line between this segment and all relative global trace ids.
     *
     * @param distributedTraceIds multi global trace ids. @see {@link DistributedTraceId}
     */
    public void relatedGlobalTraces(List<DistributedTraceId> distributedTraceIds) {
        if (distributedTraceIds == null || distributedTraceIds.size() == 0) {
            return;
        }
        for (DistributedTraceId distributedTraceId : distributedTraceIds) {
            relatedGlobalTraces.append(distributedTraceId);
        }
    }

    /**
     * After {@link Span} is finished, as be controller by "skywalking-api" module,
     * notify the {@link TraceSegment} to archive it.
     *
     * @param finishedSpan
     */
    public void archive(Span finishedSpan) {
        spans.add(finishedSpan);
    }

    /**
     * Finish this {@link TraceSegment}.
     *
     * return this, for chaining
     */
    public TraceSegment finish() {
        this.endTime = System.currentTimeMillis();
        return this;
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

    public boolean isSampled() {
        return sampled;
    }

    public void setSampled(boolean sampled) {
        this.sampled = sampled;
    }

    @Override
    public String toString() {
        return "TraceSegment{" +
            "traceSegmentId='" + traceSegmentId + '\'' +
            ", startTime=" + startTime +
            ", endTime=" + endTime +
            ", refs=" + refs +
            ", spans=" + spans +
            ", applicationCode='" + applicationCode + '\'' +
            ", relatedGlobalTraces=" + relatedGlobalTraces +
            '}';
    }
}
