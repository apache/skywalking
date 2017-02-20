package com.a.eye.skywalking.api.plugin.dubbo;

import com.a.eye.skywalking.api.IBuriedPointType;

public enum  DubboBuriedPointType implements IBuriedPointType {
    INSTANCE;

    @Override
    public String getTypeName() {
        return "D";
    }

    @Override
    public CallType getCallType() {
        return CallType.SYNC;
    }

}
