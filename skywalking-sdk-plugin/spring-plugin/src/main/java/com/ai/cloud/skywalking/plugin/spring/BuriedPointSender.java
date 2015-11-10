package com.ai.cloud.skywalking.plugin.spring;

import com.ai.cloud.skywalking.api.IBuriedPointSender;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.SendData;

public class BuriedPointSender implements IBuriedPointSender {
    public ContextData beforeSend(SendData data) {
        System.out.println("before Send");
        return null;
    }

    public void afterSend() {
        System.out.println("after Send");
    }

    public void handleException(Throwable th) {
        System.out.println("handle Exception");
    }
}
