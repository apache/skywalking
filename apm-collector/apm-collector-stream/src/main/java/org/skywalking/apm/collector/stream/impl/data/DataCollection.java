package org.skywalking.apm.collector.stream.impl.data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class DataCollection {
    private Map<String, Data> data;
    private volatile boolean isHold;

    public DataCollection() {
        this.data = new HashMap<>();
        this.isHold = false;
    }

    public void release() {
        isHold = false;
    }

    public void hold() {
        isHold = true;
    }

    public boolean isHolding() {
        return isHold;
    }

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    public void put(String key, Data value) {
        data.put(key, value);
    }

    public Data get(String key) {
        return data.get(key);
    }

    public int size() {
        return data.size();
    }

    public void clear() {
        data.clear();
    }

    public Map<String, Data> asMap() {
        return data;
    }
}
