package org.skywalking.apm.collector.worker.segment.mock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.network.proto.KeyWithStringValue;
import org.skywalking.apm.network.proto.LogMessage;
import org.skywalking.apm.network.proto.SpanObject;

/**
 * @author pengys5
 */
public enum SpanFromJson {
    INSTANCE;

    private static final String spanId = "spanId";
    private static final String parentSpanId = "parentSpanId";
    private static final String startTime = "startTime";
    private static final String endTime = "endTime";
    private static final String operationNameId = "operationNameId";
    private static final String operationName = "operationName";
    private static final String peerId = "peerId";
    private static final String peer = "peer";
    private static final String spanType = "spanType";
    private static final String spanLayer = "spanLayer";
    private static final String componentId = "componentId";
    private static final String component = "component";
    private static final String isError = "isError";
    private static final String tags = "tags";
    private static final String logs = "logs";

    public List<SpanObject.Builder> build(JsonArray spanArray) {
        List<SpanObject.Builder> spans = new LinkedList<>();
        for (int i = 0; i < spanArray.size(); i++) {
            JsonObject spanJson = spanArray.get(i).getAsJsonObject();
            SpanObject.Builder builder = SpanObject.newBuilder();
            buildSpan(builder, spanJson);
            spans.add(builder);
        }

        return spans;
    }

    private void buildSpan(SpanObject.Builder builder, JsonObject spanJsonObj) {
        if (spanJsonObj.has(spanId)) {
            builder.setSpanId(spanJsonObj.get(spanId).getAsInt());
        }
        if (spanJsonObj.has(parentSpanId)) {
            builder.setParentSpanId(spanJsonObj.get(parentSpanId).getAsInt());
        }
        if (spanJsonObj.has(startTime)) {
            builder.setStartTime(spanJsonObj.get(startTime).getAsLong());
        }
        if (spanJsonObj.has(endTime)) {
            builder.setEndTime(spanJsonObj.get(endTime).getAsLong());
        }
        if (spanJsonObj.has(operationNameId)) {
            builder.setOperationNameId(spanJsonObj.get(operationNameId).getAsInt());
        }
        if (spanJsonObj.has(operationName)) {
            builder.setOperationName(spanJsonObj.get(operationName).getAsString());
        }
        if (spanJsonObj.has(peerId)) {
            builder.setPeerId(spanJsonObj.get(peerId).getAsInt());
        }
        if (spanJsonObj.has(peer)) {
            builder.setPeer(spanJsonObj.get(peer).getAsString());
        }
        if (spanJsonObj.has(spanType)) {
            builder.setSpanTypeValue(spanJsonObj.get(spanType).getAsInt());
        }
        if (spanJsonObj.has(spanLayer)) {
            builder.setSpanLayerValue(spanJsonObj.get(spanLayer).getAsInt());
        }
        if (spanJsonObj.has(component)) {
            builder.setComponent(spanJsonObj.get(component).getAsString());
        }
        if (spanJsonObj.has(componentId)) {
            builder.setComponentId(spanJsonObj.get(componentId).getAsInt());
        }
        if (spanJsonObj.has(isError)) {
            builder.setIsError(spanJsonObj.get(isError).getAsBoolean());
        }
        if (spanJsonObj.has(tags)) {
            List<KeyWithStringValue.Builder> tagsBuilders = TagsFromJson.INSTANCE.build(spanJsonObj.get(tags).getAsJsonArray());
            tagsBuilders.forEach(tagsBuilder -> {
                builder.addTags(tagsBuilder);
            });
        }
        if (spanJsonObj.has(logs)) {
            List<LogMessage.Builder> logsBuilders = LogsFromJson.INSTANCE.build(spanJsonObj.get(logs).getAsJsonArray());
            logsBuilders.forEach(logsBuilder -> {
                builder.addLogs(logsBuilder);
            });
        }
    }
}
