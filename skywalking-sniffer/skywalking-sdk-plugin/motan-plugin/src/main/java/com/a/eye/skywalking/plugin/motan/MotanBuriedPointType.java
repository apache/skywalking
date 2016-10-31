package com.a.eye.skywalking.plugin.motan;

import com.a.eye.skywalking.api.IBuriedPointType;
import com.a.eye.skywalking.protocol.common.CallType;

public class MotanBuriedPointType implements IBuriedPointType {

    private static MotanBuriedPointType motanBuriedPointType;

    public static IBuriedPointType instance() {
        if (motanBuriedPointType == null) {
            motanBuriedPointType = new MotanBuriedPointType();
        }

        return motanBuriedPointType;
    }

    @Override
    public String getTypeName() {
        return "M";
    }

    @Override
    public CallType getCallType() {
        return CallType.SYNC;
    }

    private MotanBuriedPointType() {
        //Non
    }

}
