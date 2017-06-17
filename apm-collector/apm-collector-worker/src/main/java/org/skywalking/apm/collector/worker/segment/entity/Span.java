package org.skywalking.apm.collector.worker.segment.entity;

import com.google.gson.annotations.SerializedName;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
public class Span {

    @SerializedName("si")
    private int spanId;

    @SerializedName("ps")
    private int parentSpanId;

    @SerializedName("st")
    private long startTime;

    @SerializedName("et")
    private long endTime;

    @SerializedName("on")
    private String operationName;

    @SerializedName("ts")
    private Map<String, String> tagsWithStr = new LinkedHashMap<>();

    @SerializedName("tb")
    private Map<String, Boolean> tagsWithBool = new LinkedHashMap<>();

    @SerializedName("ti")
    private Map<String, Integer> tagsWithInt = new LinkedHashMap<>();

    @SerializedName("lo")
    private List<LogData> logs = new LinkedList<>();

    public int getSpanId() {
        return spanId;
    }

    public int getParentSpanId() {
        return parentSpanId;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public String getOperationName() {
        return operationName;
    }

    public String getStrTag(String key) {
        return tagsWithStr.get(key);
    }

    public Boolean getBoolTag(String key) {
        return tagsWithBool.get(key);
    }

    public Integer getIntTag(String key) {
        return tagsWithInt.get(key);
    }

    public List<LogData> getLogs() {
        return logs;
    }
}
