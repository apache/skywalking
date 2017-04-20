package com.a.eye.skywalking.trace;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.Map;

/**
 * It is a holder of one log record.
 *
 * Created by wusheng on 2017/2/17.
 */
public class LogData {
    @Expose
    @SerializedName(value = "tm")
    private long time;

    @Expose
    @SerializedName(value = "fi")
    private Map<String, String> fields;

    LogData(long time, Map<String, String> fields) {
        this.time = time;
        if (fields == null) {
            throw new NullPointerException();
        }
        this.fields = fields;
    }

    public LogData() {
    }

    public long getTime() {
        return time;
    }

    public Map<String, ?> getFields() {
        return Collections.unmodifiableMap(fields);
    }

}
