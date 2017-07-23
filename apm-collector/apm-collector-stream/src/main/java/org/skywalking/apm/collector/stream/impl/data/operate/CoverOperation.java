package org.skywalking.apm.collector.stream.impl.data.operate;

import org.skywalking.apm.collector.stream.impl.data.Operation;

/**
 * @author pengys5
 */
public class CoverOperation implements Operation {
    @Override public String operate(String newValue, String oldValue) {
        return newValue;
    }

    @Override public Long operate(Long newValue, Long oldValue) {
        return newValue;
    }

    @Override public Float operate(Float newValue, Float oldValue) {
        return newValue;
    }
}
