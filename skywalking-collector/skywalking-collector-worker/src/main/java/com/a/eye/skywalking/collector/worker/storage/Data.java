package com.a.eye.skywalking.collector.worker.storage;

import java.util.Map;

/**
 * @author pengys5
 */
public interface Data {
    String getId();

    void merge(Map<String, ?> dbData);
}
