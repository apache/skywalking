package com.a.eye.skywalking.collector.worker.storage;

import java.util.Map;

/**
 * @author pengys5
 */
public class SegmentData implements Data {

    private String id;
    private String segmentStr;

    public SegmentData(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public void merge(Map<String, ?> source) {
    }

    public String getSegmentStr() {
        return segmentStr;
    }

    public void setSegmentStr(String segmentStr) {
        this.segmentStr = segmentStr;
    }
}
