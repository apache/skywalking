package org.skywalking.apm.agent.core.context;

import java.util.List;
import org.skywalking.apm.agent.core.context.ids.DistributedTraceId;

/**
 * The <code>ContextSnapshot</code> is a snapshot for current context.
 *
 * @author wusheng
 */
public class ContextSnapshot {
    private String traceSegmentId;

    private int spanId = -1;

    /**
     * {@link DistributedTraceId}
     */
    private List<DistributedTraceId> distributedTraceIds;

    ContextSnapshot(String traceSegmentId, int spanId,
        List<DistributedTraceId> distributedTraceIds) {
        this.traceSegmentId = traceSegmentId;
        this.spanId = spanId;
        this.distributedTraceIds = distributedTraceIds;
    }

    public List<DistributedTraceId> getDistributedTraceIds() {
        return distributedTraceIds;
    }

    public String getTraceSegmentId() {
        return traceSegmentId;
    }

    public int getSpanId() {
        return spanId;
    }
}
