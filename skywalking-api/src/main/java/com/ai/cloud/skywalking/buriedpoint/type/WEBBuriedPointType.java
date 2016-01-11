package com.ai.cloud.skywalking.buriedpoint.type;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.model.CallType;

public class WEBBuriedPointType implements IBuriedPointType {
    @Override
    public String getTypeName() {
        return "W";
    }

    @Override
    public CallType getCallType() {
        return CallType.ASYNC;
    }
}
