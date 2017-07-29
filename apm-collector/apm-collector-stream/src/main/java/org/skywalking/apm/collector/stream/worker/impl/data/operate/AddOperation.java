package org.skywalking.apm.collector.stream.worker.impl.data.operate;

import org.skywalking.apm.collector.stream.worker.impl.data.Operation;

/**
 * @author pengys5
 */
public class AddOperation implements Operation {

    @Override public String operate(String newValue, String oldValue) {
        throw new UnsupportedOperationException("not support string addition operation");
    }

    @Override public Long operate(Long newValue, Long oldValue) {
        return newValue + oldValue;
    }

    @Override public Float operate(Float newValue, Float oldValue) {
        return newValue + oldValue;
    }

    @Override public Integer operate(Integer newValue, Integer oldValue) {
        return newValue + oldValue;
    }

    @Override public byte[] operate(byte[] newValue, byte[] oldValue) {
        throw new UnsupportedOperationException("not support byte addition operation");
    }
}
