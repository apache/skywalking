package com.a.eye.skywalking.collector.worker.segment.entity;

import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class LogData extends DeserializeObject {
    private long time;
    private Map<String, String> fields;

    public long getTime() {
        return time;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public LogData deserialize(JsonReader reader) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");

        boolean first = true;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("tm")) {
                Long tm = reader.nextLong();
                this.time = tm;
                JsonBuilder.INSTANCE.append(stringBuilder, "tm", tm, first);
            } else if (name.equals("fi")) {
                fields = new HashMap<>();
                reader.beginObject();

                while (reader.hasNext()) {
                    String key = reader.nextName();
                    String value = reader.nextString();
                    fields.put(key, value);
                }
                reader.endObject();
                JsonBuilder.INSTANCE.append(stringBuilder, "fi", fields, first);
            } else {
                reader.skipValue();
            }
            first = false;
        }
        reader.endObject();
        stringBuilder.append("}");
        this.setJsonStr(stringBuilder.toString());
        return this;
    }
}
