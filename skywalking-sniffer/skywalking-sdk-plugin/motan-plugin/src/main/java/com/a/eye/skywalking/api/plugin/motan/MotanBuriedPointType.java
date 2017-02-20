package com.a.eye.skywalking.api.plugin.motan;

import com.a.eye.skywalking.api.IBuriedPointType;

public enum MotanBuriedPointType implements IBuriedPointType {

    INSTANCE;

    @Override
    public String getTypeName() {
        return "MO";
    }

    @Override
    public CallType getCallType() {
        return CallType.SYNC;
    }

}
