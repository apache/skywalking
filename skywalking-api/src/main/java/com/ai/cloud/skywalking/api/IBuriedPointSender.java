package com.ai.cloud.skywalking.api;

import com.ai.cloud.skywalking.model.SendData;
import com.ai.cloud.skywalking.model.ContextData;

public interface IBuriedPointSender extends IExceptionHandler {
    ContextData beforeSend(SendData data);

    void afterSend();
}
