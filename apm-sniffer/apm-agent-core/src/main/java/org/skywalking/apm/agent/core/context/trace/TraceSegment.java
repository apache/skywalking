package org.skywalking.apm.agent.core.context.trace;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.skywalking.apm.agent.core.context.ids.DistributedTraceIds;
import org.skywalking.apm.agent.core.context.ids.GlobalIdGenerator;
import org.skywalking.apm.agent.core.context.ids.NewDistributedTraceId;
import org.skywalking.apm.agent.core.dictionary.DictionaryManager;
import org.skywalking.apm.agent.core.dictionary.PossibleFound;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

/**
 * {@link TraceSegment} is a segment or fragment of the distributed trace.
 * {@see https://github.com/opentracing/specification/blob/master/specification.md#the-opentracing-data-model}
 * A {@link
 * TraceSegment} means the segment, which exists in current {@link Thread}. And the distributed trace is formed by multi
 * {@link TraceSegment}s, because the distributed trace crosses multi-processes, multi-threads.
 * <p>
 *
 * @author wusheng
 */
public class TraceSegment {
    private static final ILog logger = LogManager.getLogger(TraceSegment.class);

    private static final String ID_TYPE = "S";

    /**
     * The id of this trace segment.
     * Every segment has its unique-global-id.
     */
    private String traceSegmentId;

    /**
     * The start time of this trace segment.
     */
    private long startTime;

    /**
     * The end time of this trace segment.
     */
    private long endTime;

    /**
     * The refs of parent trace segments, except the primary one.
     * For most RPC call, {@link #refs} contains only one element,
     * but if this segment is a start span of batch process, the segment faces multi parents,
     * at this moment, we use this {@link #refs} to link them.
     */
    private List<TraceSegmentRef> refs;

    /**
     * The spans belong to this trace segment.
     * They all have finished.
     * All active spans are hold and controlled by "skywalking-api" module.
     */
    private List<AbstractTracingSpan> spans;

    /**
     * The <code>applicationId</code> represents a name of current application/JVM and indicates which is business
     * role in the cluster.
     * <p>
     * e.g. account_app, billing_app
     */
    private int applicationId;

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
    private DistributedTraceIds relatedGlobalTraces;

    private boolean ignore = false;

    /**
     * Create a default/empty trace segment,
     * with current time as start time,
     * and generate a new segment id.
     */
    public TraceSegment() {
        this.applicationId = (Integer)DictionaryManager.findApplicationCodeSection()
            .find(Config.Agent.APPLICATION_CODE)
            .doInCondition(
                new PossibleFound.FoundAndObtain() {
                    @Override public Object doProcess(int applicationId) {
                        return applicationId;
                    }
                },
                new PossibleFound.NotFoundAndObtain() {
                    @Override public Object doProcess() {
                        throw new IllegalStateException("Application id must not NULL.");
                    }
                }
            );
        this.startTime = System.currentTimeMillis();
        this.traceSegmentId = GlobalIdGenerator.generate(ID_TYPE);
        this.spans = new LinkedList<AbstractTracingSpan>();
        this.relatedGlobalTraces = new DistributedTraceIds();
        this.relatedGlobalTraces.append(new NewDistributedTraceId());
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
     * After {@link AbstractSpan} is finished, as be controller by "skywalking-api" module,
     * notify the {@link TraceSegment} to archive it.
     *
     * @param finishedSpan
     */
    public void archive(AbstractTracingSpan finishedSpan) {
        spans.add(finishedSpan);
    }

    /**
     * Finish this {@link TraceSegment}.
     * <p>
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

    public int getApplicationId() {
        return applicationId;
    }

    public boolean hasRef() {
        return !(refs == null || refs.size() == 0);
    }

    public List<TraceSegmentRef> getRefs() {
        return refs;
    }

    public List<DistributedTraceId> getRelatedGlobalTraces() {
        return relatedGlobalTraces.getRelatedGlobalTraces();
    }

    public boolean isSingleSpanSegment() {
        return this.spans != null && this.spans.size() == 1;
    }

    public boolean isIgnore() {
        return ignore;
    }

    public void setIgnore(boolean ignore) {
        this.ignore = ignore;
    }

    @Override
    public String toString() {
        return "TraceSegment{" +
            "traceSegmentId='" + traceSegmentId + '\'' +
            ", startTime=" + startTime +
            ", endTime=" + endTime +
            ", refs=" + refs +
            ", spans=" + spans +
            ", applicationId='" + applicationId + '\'' +
            ", relatedGlobalTraces=" + relatedGlobalTraces +
            '}';
    }
}
