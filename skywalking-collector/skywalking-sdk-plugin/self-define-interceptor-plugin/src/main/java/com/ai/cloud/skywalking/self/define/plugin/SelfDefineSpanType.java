package com.ai.cloud.skywalking.self.define.plugin;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.protocol.common.CallType;

public class SelfDefineSpanType implements IBuriedPointType {
    @Override
    public String getTypeName() {
        return "L";
    }

    @Override
    public CallType getCallType() {
        return CallType.SYNC;
    }
}
