package org.skywalking.apm.trace.TraceId;

/**
 * The <code>PropagatedTraceId</code> represents a {@link DistributedTraceId}, which is propagated from the peer.
 *
 * @author wusheng
 */
public class PropagatedTraceId extends DistributedTraceId {
    public PropagatedTraceId(String id) {
        super(id);
    }
}
