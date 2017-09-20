package org.skywalking.apm.agent.core.context;

/**
 * @author wusheng
 */
public class CarrierItemHead extends CarrierItem {
    public CarrierItemHead(CarrierItem next) {
        super("", "", next);
    }
}
