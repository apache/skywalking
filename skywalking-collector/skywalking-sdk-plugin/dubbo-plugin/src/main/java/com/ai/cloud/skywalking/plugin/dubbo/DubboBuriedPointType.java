package com.ai.cloud.skywalking.plugin.dubbo;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.protocol.CallType;

public class DubboBuriedPointType implements IBuriedPointType {

    private static DubboBuriedPointType dubboBuriedPointType;

    public static IBuriedPointType instance() {
        if (dubboBuriedPointType == null) {
            dubboBuriedPointType = new DubboBuriedPointType();
        }

        return dubboBuriedPointType;
    }

    @Override
    public String getTypeName() {
        return "D";
    }

    @Override
    public CallType getCallType() {
        return CallType.SYNC;
    }

    private DubboBuriedPointType() {
        //Non
    }

}
