package org.skywalking.apm.agent.core.context;

/**
 * The <code>Injectable</code> represents a callback
 *
 * @author wusheng
 */
public interface Injectable {
    /**
     * @param injectedCarrier notify the <code>Injectable</code> the {@link ContextCarrier} has been injected.
     */
    void notify(ContextCarrier injectedCarrier);

    /**
     * @return peer, represent ipv4, ipv6, hostname, or cluster addresses list.
     */
    String getPeer();
}
