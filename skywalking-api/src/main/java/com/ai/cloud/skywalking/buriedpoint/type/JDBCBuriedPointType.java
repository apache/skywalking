package com.ai.cloud.skywalking.buriedpoint.type;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.model.CallType;

public class JDBCBuriedPointType implements IBuriedPointType {
    @Override
    public String getTypeName() {
        return "J";
    }

    @Override
    public CallType getCallType() {
        return CallType.LOCAL;
    }
}
