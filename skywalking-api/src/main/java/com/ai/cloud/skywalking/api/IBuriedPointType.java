package com.ai.cloud.skywalking.api;

import com.ai.cloud.skywalking.model.CallType;

public interface IBuriedPointType {

    String getTypeName();

    CallType getCallType();
}
