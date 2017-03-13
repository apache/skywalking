package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.selector.AbstractHashMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MetricData extends AbstractHashMessage {

    public MetricData(String key) {
        super(key);
        this.id = key;
    }

    private String id;

    private static final String s10 = "s10";
    private static final String s20 = "s20";
    private static final String s30 = "s30";
    private static final String s40 = "s40";
    private static final String s50 = "s50";
    private static final String s60 = "s60";

    private Long s10Value = 0L;
    private Long s20Value = 0L;
    private Long s30Value = 0L;
    private Long s40Value = 0L;
    private Long s50Value = 0L;
    private Long s60Value = 0L;

    public void setMetric(int second, Long value) {
        if (second <= 10) {
            s10Value += value;
        } else if (second > 10 && second <= 20) {
            s20Value += value;
        } else if (second > 20 && second <= 30) {
            s30Value += value;
        } else if (second > 30 && second <= 40) {
            s40Value += value;
        } else if (second > 40 && second <= 50) {
            s50Value += value;
        } else {
            s60Value += value;
        }
    }

    public void merge(MetricData metricData) {
        s10Value += metricData.s10Value;
        s20Value += metricData.s20Value;
        s30Value += metricData.s30Value;
        s40Value += metricData.s40Value;
        s50Value += metricData.s50Value;
        s60Value += metricData.s60Value;
    }

    public void merge(Map<String, Object> dbData) {
        s10Value += Long.valueOf(dbData.get(s10).toString());
        s20Value += Long.valueOf(dbData.get(s20).toString());
        s30Value += Long.valueOf(dbData.get(s30).toString());
        s40Value += Long.valueOf(dbData.get(s40).toString());
        s50Value += Long.valueOf(dbData.get(s50).toString());
        s60Value += Long.valueOf(dbData.get(s60).toString());
    }

    public Map<String, Long> toMap() {
        Map<String, Long> map = new HashMap<>();
        map.put(s10, s10Value);
        map.put(s20, s20Value);
        map.put(s30, s30Value);
        map.put(s40, s40Value);
        map.put(s50, s50Value);
        map.put(s60, s60Value);
        return map;
    }

    public String getId() {
        return id;
    }

    protected Long getS10Value() {
        return s10Value;
    }

    protected Long getS20Value() {
        return s20Value;
    }

    protected Long getS30Value() {
        return s30Value;
    }

    protected Long getS40Value() {
        return s40Value;
    }

    protected Long getS50Value() {
        return s50Value;
    }

    protected Long getS60Value() {
        return s60Value;
    }
}
