package org.skywalking.apm.collector.worker.segment.mock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.network.proto.KeyWithStringValue;
import org.skywalking.apm.network.proto.LogMessage;

/**
 * @author pengys5
 */
public enum LogsFromJson {
    INSTANCE;

    private static final String time = "time";
    private static final String data = "data";

    public List<LogMessage.Builder> build(JsonArray logsArray) {
        List<LogMessage.Builder> logs = new LinkedList<>();
        for (int i = 0; i < logsArray.size(); i++) {
            JsonObject logJson = logsArray.get(i).getAsJsonObject();
            LogMessage.Builder builder = LogMessage.newBuilder();
            buildLogMessage(builder, logJson);
            logs.add(builder);
        }
        return logs;
    }

    private void buildLogMessage(LogMessage.Builder builder, JsonObject logJson) {
        if (logJson.has(time)) {
            builder.setTime(logJson.get(time).getAsLong());
        }
        if (logJson.has(data)) {
            List<KeyWithStringValue.Builder> logsBuilders = TagsFromJson.INSTANCE.build(logJson.get(data).getAsJsonArray());
            logsBuilders.forEach(logsBuilder -> {
                builder.addData(logsBuilder);
            });
        }
    }
}
