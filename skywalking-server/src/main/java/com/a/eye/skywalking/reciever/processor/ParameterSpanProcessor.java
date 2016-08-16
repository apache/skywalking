package com.a.eye.skywalking.reciever.processor;

import com.a.eye.skywalking.protocol.common.AbstractDataSerializable;
import com.a.eye.skywalking.reciever.util.HBaseUtil;

import java.util.List;

public abstract class ParameterSpanProcessor extends AbstractSpanProcessor {
    @Override
    public void process(List<AbstractDataSerializable> serializedObjects) {
        doSaveHBase(HBaseUtil.getConnection(), serializedObjects);
    }

    @Override
    public void doAlarm(List<AbstractDataSerializable> serializedObjects) {
        // do Nothing
    }
}
