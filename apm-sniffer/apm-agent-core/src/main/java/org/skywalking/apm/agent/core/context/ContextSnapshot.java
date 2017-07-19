package org.skywalking.apm.agent.core.context;

import java.util.List;
import org.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.skywalking.apm.util.StringUtil;

/**
 * The <code>ContextSnapshot</code> is a snapshot for current context. The snapshot carries the info for building
 * reference between two segments in two thread, but have a causal relationship.
 *
 * @author wusheng
 */
public class ContextSnapshot {
    /**
     * trace segment id of the parent trace segment.
     */
    private String traceSegmentId;

    /**
     * span id of the parent span, in parent trace segment.
     */
    private int spanId = -1;

    private String entryOperationName;

    /**
     * {@link DistributedTraceId}
     */
    private List<DistributedTraceId> distributedTraceIds;

    ContextSnapshot(String traceSegmentId, int spanId,
        List<DistributedTraceId> distributedTraceIds, int entryServiceId, String entryOperationName) {
        this.traceSegmentId = traceSegmentId;
        this.spanId = spanId;
        this.distributedTraceIds = distributedTraceIds;

        if (entryServiceId == DictionaryUtil.nullValue()) {
            this.entryOperationName = "#" + entryOperationName;
        } else {
            this.entryOperationName = String.valueOf(entryServiceId);
        }
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

    public boolean isValid() {
        return traceSegmentId != null
            && spanId > -1
            && distributedTraceIds != null
            && distributedTraceIds.size() > 0
            && !StringUtil.isEmpty(entryOperationName);
    }

    public String getEntryOperationName() {
        return entryOperationName;
    }
}
