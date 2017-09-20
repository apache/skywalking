package org.skywalking.apm.agent.core.context;

/**
 * @author wusheng
 */
public class TraceContextCarrierItem extends CarrierItem {
    private static final String HEAD_NAME = "Trace-Context";

    public TraceContextCarrierItem(String headValue, CarrierItem next) {
        super(HEAD_NAME, headValue, next);
    }
}
