package com.a.eye.skywalking.plugin.httpClient.v4;

import com.a.eye.skywalking.api.IBuriedPointType;
import com.a.eye.skywalking.protocol.common.CallType;

public class WebBuriedPointType implements IBuriedPointType {

    private static WebBuriedPointType webBuriedPointType;

    public static IBuriedPointType instance() {
        if (webBuriedPointType == null) {
            webBuriedPointType = new WebBuriedPointType();
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

    private WebBuriedPointType() {
        // Non
    }
}
