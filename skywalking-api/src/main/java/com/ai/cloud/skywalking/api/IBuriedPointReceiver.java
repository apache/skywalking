package com.ai.cloud.skywalking.api;

import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.Identification;

public interface IBuriedPointReceiver extends IExceptionHandler {
    void afterReceived();

    void beforeReceived(ContextData contextData, Identification id);
}
