package org.skywalking.apm.agent.core.context.ids;

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
