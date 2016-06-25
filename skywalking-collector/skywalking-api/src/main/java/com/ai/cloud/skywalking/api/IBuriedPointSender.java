package com.ai.cloud.skywalking.api;

import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.model.ContextData;

public interface IBuriedPointSender extends IExceptionHandler {
    ContextData beforeSend(Identification id);

    void afterSend();
}
