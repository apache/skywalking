package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.cache.ServiceNameCache;
import org.skywalking.apm.collector.ui.dao.ISegmentDAO;
import org.skywalking.apm.network.proto.KeyWithStringValue;
import org.skywalking.apm.network.proto.LogMessage;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.TraceSegmentObject;

/**
 * @author pengys5
 */
public class SpanService {

    public JsonObject load(String segmentId, int spanId) {
        ISegmentDAO segmentDAO = (ISegmentDAO)DAOContainer.INSTANCE.get(ISegmentDAO.class.getName());
        TraceSegmentObject segmentObject = segmentDAO.load(segmentId);

        JsonObject spanJson = new JsonObject();
        List<SpanObject> spans = segmentObject.getSpansList();
        for (SpanObject spanObject : spans) {
            if (spanId == spanObject.getSpanId()) {
                String operationName = spanObject.getOperationName();
                if (spanObject.getOperationNameId() != 0) {
                    operationName = ServiceNameCache.get(spanObject.getOperationNameId());
                }
                spanJson.addProperty("operationName", operationName);
                spanJson.addProperty("startTime", spanObject.getStartTime());
                spanJson.addProperty("endTime", spanObject.getEndTime());

                JsonArray logsArray = new JsonArray();
                List<LogMessage> logs = spanObject.getLogsList();
                for (LogMessage logMessage : logs) {
                    JsonObject logJson = new JsonObject();
                    logJson.addProperty("time", logMessage.getTime());

                    JsonArray logInfoArray = new JsonArray();
                    for (KeyWithStringValue value : logMessage.getDataList()) {
                        JsonObject valueJson = new JsonObject();
                        valueJson.addProperty("key", value.getKey());
                        valueJson.addProperty("value", value.getValue());
                        logInfoArray.add(valueJson);
                    }
                    logJson.add("logInfo", logInfoArray);
                    logsArray.add(logJson);
                }
                spanJson.add("logMessage", logsArray);

                JsonArray tagsArray = new JsonArray();
                for (KeyWithStringValue tagValue : spanObject.getTagsList()) {
                    JsonObject tagJson = new JsonObject();
                    tagJson.addProperty("key", tagValue.getKey());
                    tagJson.addProperty("value", tagValue.getValue());
                    tagsArray.add(tagJson);
                }
                spanJson.add("tags", tagsArray);
            }
        }

        return spanJson;
    }
}