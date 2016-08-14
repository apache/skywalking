package com.a.eye.skywalking.reciever.processor;

import com.a.eye.skywalking.protocol.common.AbstractDataSerializable;

import java.util.List;

public interface IProcessor {
    int getProtocolType();

    void process(List<AbstractDataSerializable> serializedObjects);
}
