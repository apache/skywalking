package com.a.eye.skywalking.api;

import com.a.eye.skywalking.protocol.common.CallType;

public interface IBuriedPointType {

    String getTypeName();

    CallType getCallType();
}
