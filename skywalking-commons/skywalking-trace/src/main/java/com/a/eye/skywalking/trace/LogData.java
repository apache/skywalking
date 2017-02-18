package com.a.eye.skywalking.trace;

import java.util.Map;

/**
 * It is a holder of one log record.
 *
 * Created by wusheng on 2017/2/17.
 */
public class LogData {
    private final long time;
    private final Map<String, ?> fields;

    LogData(long time, Map<String, ?> fields) {
        this.time = time;
        this.fields = fields;
    }

    public long getTime() {
        return time;
    }

    public Map<String, ?> getFields() {
        return fields;
    }
}
