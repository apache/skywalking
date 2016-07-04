package com.ai.cloud.skywalking.plugin.spring;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.protocol.common.CallType;

public class SpringBuriedPointType implements IBuriedPointType {

    private static SpringBuriedPointType springBuriedPointType;

    public static IBuriedPointType instance() {
        if (springBuriedPointType == null) {
            springBuriedPointType = new SpringBuriedPointType();
        }

        return springBuriedPointType;
    }

    @Override
    public String getTypeName() {
        return "M";
    }

    @Override
    public CallType getCallType() {
        return CallType.LOCAL;
    }

    private SpringBuriedPointType() {
        // Non
    }

}
