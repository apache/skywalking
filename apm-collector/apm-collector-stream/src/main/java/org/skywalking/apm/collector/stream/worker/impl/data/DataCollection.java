package org.skywalking.apm.collector.stream.worker.impl.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.skywalking.apm.collector.core.stream.Data;

/**
 * @author pengys5
 */
public class DataCollection {
    private Map<String, Data> data;
    private volatile boolean writing;
    private volatile boolean reading;

    public DataCollection() {
        this.data = new ConcurrentHashMap<>();
        this.writing = false;
        this.reading = false;
    }

    public void finishWriting() {
        writing = false;
    }

    public void writing() {
        writing = true;
    }

    public boolean isWriting() {
        return writing;
    }

    public void finishReading() {
        reading = false;
    }

    public void reading() {
        reading = true;
    }

    public boolean isReading() {
        return reading;
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
