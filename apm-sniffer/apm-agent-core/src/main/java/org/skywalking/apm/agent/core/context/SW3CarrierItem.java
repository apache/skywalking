package org.skywalking.apm.agent.core.context;

/**
 * @author wusheng
 */
public class SW3CarrierItem extends CarrierItem {
    public static final String HEADER_NAME = "sw3";
    private ContextCarrier carrier;

    public SW3CarrierItem(ContextCarrier carrier, CarrierItem next) {
        super(HEADER_NAME, carrier.serialize(), next);
        this.carrier = carrier;
    }

    @Override
    public void setHeadValue(String headValue) {
        carrier.deserialize(headValue);
    }
}
