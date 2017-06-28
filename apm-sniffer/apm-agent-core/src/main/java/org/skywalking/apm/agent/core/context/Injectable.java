package org.skywalking.apm.agent.core.context;

/**
 * The <code>Injectable</code> represents a callback
 *
 * @author wusheng
 */
public interface Injectable {
    ContextCarrier getCarrier();

    /**
     * @return peer, represent ipv4, ipv6, hostname, or cluster addresses list.
     */
    String getPeer();
}
