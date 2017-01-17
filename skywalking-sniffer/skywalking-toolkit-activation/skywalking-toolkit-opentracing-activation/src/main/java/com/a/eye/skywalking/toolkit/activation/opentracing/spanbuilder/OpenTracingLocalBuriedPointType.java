package com.a.eye.skywalking.toolkit.activation.opentracing.spanbuilder;

import com.a.eye.skywalking.api.IBuriedPointType;

/**
 * @author zhangxin
 */
public enum  OpenTracingLocalBuriedPointType implements IBuriedPointType {
    INSTANCE;

    @Override
    public String getTypeName() {
        return "OT";
    }

    @Override
    public CallType getCallType() {
        return CallType.LOCAL;
    }
}
