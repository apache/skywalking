package com.ai.cloud.skywalking.reciever.processor;

import com.ai.cloud.skywalking.protocol.common.AbstractDataSerializable;

import java.util.List;

public interface IProcessor {
    void process(List<AbstractDataSerializable> serializedObjects);
}
