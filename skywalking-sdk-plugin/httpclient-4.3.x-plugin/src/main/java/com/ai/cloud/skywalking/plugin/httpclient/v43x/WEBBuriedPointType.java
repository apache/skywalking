package com.ai.cloud.skywalking.plugin.httpclient.v43x;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.protocol.CallType;

public class WEBBuriedPointType implements IBuriedPointType {

    private static WEBBuriedPointType webBuriedPointType;

    public static IBuriedPointType instance() {
        if (webBuriedPointType == null) {
            webBuriedPointType = new WEBBuriedPointType();
        }

        return webBuriedPointType;
    }

    @Override
    public String getTypeName() {
        return "W";
    }

    @Override
    public CallType getCallType() {
        return CallType.SYNC;
    }

    private WEBBuriedPointType() {
        // Non
    }
}
