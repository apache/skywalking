package com.a.eye.skywalking.api.plugin.httpClient.v4;

import com.a.eye.skywalking.api.IBuriedPointType;

public enum  WebBuriedPointType implements IBuriedPointType {
    INSTANCE;

    @Override
    public String getTypeName() {
        return "W";
    }

    @Override
    public CallType getCallType() {
        return CallType.SYNC;
    }

}
