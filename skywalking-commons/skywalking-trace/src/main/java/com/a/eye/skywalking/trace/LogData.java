package com.a.eye.skywalking.trace;

import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.trace.proto.KeyValue;
import com.a.eye.skywalking.trace.proto.LogDataMessage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * It is a holder of one log record.
 *
 * Created by wusheng on 2017/2/17.
 */
public class LogData {
    private long time;
    private Map<String, ?> fields;

    LogData(long time, Map<String, ?> fields) {
        this.time = time;
        if(fields == null){
            throw new NullPointerException();
        }
        this.fields = fields;
    }

    LogData(LogDataMessage message){
        deserialize(message);
    }

    public long getTime() {
        return time;
    }

    public Map<String, ?> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    public LogDataMessage serialize() {
        LogDataMessage.Builder logDataBuilder = LogDataMessage.newBuilder();
        logDataBuilder.setTime(time);

        if(fields != null){
            for (Map.Entry<String, ?> entry : fields.entrySet()) {
                KeyValue.Builder logEntryBuilder = KeyValue.newBuilder();

                logEntryBuilder.setKey(entry.getKey());
                String value = String.valueOf(entry.getValue());
                if(!StringUtil.isEmpty(value)) {
                    logEntryBuilder.setValue(value);
                }

                logDataBuilder.addFields(logEntryBuilder);
            }
        }
        return logDataBuilder.build();
    }

    public void deserialize(LogDataMessage message) {
        time = message.getTime();
        List<KeyValue> list = message.getFieldsList();
        if(list != null){
            HashMap initFields = new HashMap<String, String>();
            for (KeyValue field : list) {
                initFields.put(field.getKey(), field.getValue());
            }
            this.fields = initFields;
        }
    }
}
