package com.ai.cloud.skywalking.buriedpoint.type;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.model.CallType;

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
        return CallType.ASYNC;
    }

    private DubboBuriedPointType() {
        //Non
    }

}
