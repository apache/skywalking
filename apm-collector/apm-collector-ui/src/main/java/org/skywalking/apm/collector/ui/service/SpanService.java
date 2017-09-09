package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.cache.ApplicationCache;
import org.skywalking.apm.collector.ui.cache.ServiceNameCache;
import org.skywalking.apm.collector.ui.dao.ISegmentDAO;
import org.skywalking.apm.network.proto.KeyWithStringValue;
import org.skywalking.apm.network.proto.LogMessage;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

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
                    String serviceName = ServiceNameCache.get(spanObject.getOperationNameId());
                    if (StringUtils.isNotEmpty(serviceName)) {
                        operationName = serviceName.split(Const.ID_SPLIT)[1];
                    }
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

                JsonObject spanTypeJson = new JsonObject();
                spanTypeJson.addProperty("key", "span type");
                spanTypeJson.addProperty("value", spanObject.getSpanType().name());
                tagsArray.add(spanTypeJson);

                JsonObject componentJson = new JsonObject();
                componentJson.addProperty("key", "component");
                if (spanObject.getComponentId() == 0) {
                    componentJson.addProperty("value", spanObject.getComponent());
                } else {
                    componentJson.addProperty("value", ComponentsDefine.getInstance().getComponentName(spanObject.getComponentId()));
                }
                tagsArray.add(componentJson);

                JsonObject peerJson = new JsonObject();
                peerJson.addProperty("key", "peer");
                if (spanObject.getPeerId() == 0) {
                    peerJson.addProperty("value", spanObject.getPeer());
                } else {
                    peerJson.addProperty("value", ApplicationCache.getForUI(spanObject.getPeerId()));
                }
                tagsArray.add(peerJson);

                for (KeyWithStringValue tagValue : spanObject.getTagsList()) {
                    JsonObject tagJson = new JsonObject();
                    tagJson.addProperty("key", tagValue.getKey());
                    tagJson.addProperty("value", tagValue.getValue());
                    tagsArray.add(tagJson);
                }

                JsonObject isErrorJson = new JsonObject();
                isErrorJson.addProperty("key", "is error");
                isErrorJson.addProperty("value", spanObject.getIsError());
                tagsArray.add(isErrorJson);

                spanJson.add("tags", tagsArray);
            }
        }

        return spanJson;
    }
}