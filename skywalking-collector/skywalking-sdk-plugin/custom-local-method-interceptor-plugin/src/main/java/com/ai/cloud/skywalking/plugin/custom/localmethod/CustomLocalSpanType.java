package com.ai.cloud.skywalking.plugin.custom.localmethod;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.protocol.common.CallType;

public class CustomLocalSpanType implements IBuriedPointType {
    @Override
    public String getTypeName() {
        return "L";
    }

    @Override
    public CallType getCallType() {
        return CallType.SYNC;
    }
}
