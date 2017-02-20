package com.a.eye.skywalking.api.plugin.jdbc.define;

import com.a.eye.skywalking.api.IBuriedPointType;

public enum  JDBCBuriedPointType implements IBuriedPointType {

    INSTANCE;

    @Override
    public String getTypeName() {
        return "J";
    }

    @Override
    public CallType getCallType() {
        return CallType.LOCAL;
    }

}
