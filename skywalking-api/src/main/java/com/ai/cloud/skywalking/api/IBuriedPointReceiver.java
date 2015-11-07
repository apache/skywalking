package com.ai.cloud.skywalking.api;

import com.ai.cloud.skywalking.model.ContextData;

public interface IBuriedPointReceiver extends IExceptionHandler {
    void afterReceived();

    void beforeReceived(ContextData contextData);
}
