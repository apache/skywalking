package org.skywalking.apm.agent.core.remote;

/**
 * @author wusheng
 */
public interface GRPCChannelListener {
    void statusChanged(GRPCChannelStatus status);
}
