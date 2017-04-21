package com.a.eye.skywalking.collector.worker.segment.entity;

import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
public class Span extends DeserializeObject {
    private int spanId;
    private int parentSpanId;
    private long startTime;
    private long endTime;
    private String operationName;
    private Map<String, String> tagsWithStr;
    private Map<String, Boolean> tagsWithBool;
    private Map<String, Integer> tagsWithInt;
    private List<LogData> logs;

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

    public Span deserialize(JsonReader reader) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");

        boolean first = true;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("si")) {
                Integer si = reader.nextInt();
                this.spanId = si;
                JsonBuilder.INSTANCE.append(stringBuilder, "si", si, first);
            } else if (name.equals("ps")) {
                Integer ps = reader.nextInt();
                this.parentSpanId = ps;
                JsonBuilder.INSTANCE.append(stringBuilder, "ps", ps, first);
            } else if (name.equals("st")) {
                Long st = reader.nextLong();
                this.startTime = st;
                JsonBuilder.INSTANCE.append(stringBuilder, "st", st, first);
            } else if (name.equals("et")) {
                Long et = reader.nextLong();
                this.endTime = et;
                JsonBuilder.INSTANCE.append(stringBuilder, "et", et, first);
            } else if (name.equals("on")) {
                String on = reader.nextString();
                this.operationName = on;
                JsonBuilder.INSTANCE.append(stringBuilder, "on", on, first);
            } else if (name.equals("ts")) {
                tagsWithStr = new HashMap<>();
                reader.beginObject();

                while (reader.hasNext()) {
                    String key = reader.nextName();
                    String value = reader.nextString();
                    tagsWithStr.put(key, value);
                }
                reader.endObject();
                JsonBuilder.INSTANCE.append(stringBuilder, "ts", tagsWithStr, first);
            } else if (name.equals("tb")) {
                tagsWithBool = new HashMap<>();
                reader.beginObject();

                while (reader.hasNext()) {
                    String key = reader.nextName();
                    boolean value = reader.nextBoolean();
                    tagsWithBool.put(key, value);
                }
                reader.endObject();
                JsonBuilder.INSTANCE.append(stringBuilder, "tb", tagsWithBool, first);
            } else if (name.equals("ti")) {
                tagsWithInt = new HashMap<>();
                reader.beginObject();

                while (reader.hasNext()) {
                    String key = reader.nextName();
                    Integer value = reader.nextInt();
                    tagsWithInt.put(key, value);
                }
                reader.endObject();
                JsonBuilder.INSTANCE.append(stringBuilder, "ti", tagsWithInt, first);
            } else if (name.equals("lo")) {
                logs = new ArrayList<>();
                reader.beginArray();

                while (reader.hasNext()) {
                    LogData logData = new LogData();
                    logData.deserialize(reader);
                    logs.add(logData);
                }
                reader.endArray();
                JsonBuilder.INSTANCE.append(stringBuilder, "lo", logs, first);
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
