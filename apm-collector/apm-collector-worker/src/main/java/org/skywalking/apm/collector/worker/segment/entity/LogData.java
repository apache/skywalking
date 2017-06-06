package org.skywalking.apm.collector.worker.segment.entity;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * @author pengys5
 */
public class LogData {

    @SerializedName("tm")
    private long time;

    @SerializedName("fi")
    private Map<String, String> fields;

    public long getTime() {
        return time;
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
