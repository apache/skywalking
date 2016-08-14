package com.a.eye.skywalking.plugin.custom.localmethod;

import com.a.eye.skywalking.api.IBuriedPointType;
import com.a.eye.skywalking.protocol.common.CallType;

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
