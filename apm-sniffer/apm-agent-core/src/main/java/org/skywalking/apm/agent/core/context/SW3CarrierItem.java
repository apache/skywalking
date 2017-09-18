package org.skywalking.apm.agent.core.context;

/**
 * @author wusheng
 */
public class SW3CarrierItem extends CarrierItem {
    private static final String HEAD_NAME = "sw3";
    private ContextCarrier carrier;

    public SW3CarrierItem(ContextCarrier carrier, CarrierItem next) {
        super(HEAD_NAME, carrier.serialize(), next);
        this.carrier = carrier;
    }

    @Override
    public void setHeadValue(String headValue) {
        carrier.deserialize(headValue);
    }
}
