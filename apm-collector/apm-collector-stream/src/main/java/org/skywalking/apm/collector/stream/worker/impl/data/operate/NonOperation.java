package org.skywalking.apm.collector.stream.worker.impl.data.operate;

import org.skywalking.apm.collector.stream.worker.impl.data.Operation;

/**
 * @author pengys5
 */
public class NonOperation implements Operation {
    @Override public String operate(String newValue, String oldValue) {
        return oldValue;
    }

    @Override public Long operate(Long newValue, Long oldValue) {
        return oldValue;
    }

    @Override public Float operate(Float newValue, Float oldValue) {
        return oldValue;
    }

    @Override public Integer operate(Integer newValue, Integer oldValue) {
        return oldValue;
    }
}
