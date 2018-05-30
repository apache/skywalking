/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IComponentLibraryCatalogService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentUIDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.network.proto.KeyWithStringValue;
import org.apache.skywalking.apm.network.proto.LogMessage;
import org.apache.skywalking.apm.network.proto.SpanObject;
import org.apache.skywalking.apm.network.proto.TraceSegmentObject;

/**
 * @author peng-yongsheng
 */
public class SpanService {

    private final ISegmentUIDAO segmentDAO;
    private final ServiceNameCacheService serviceNameCacheService;
    private final ApplicationCacheService applicationCacheService;
    private final IComponentLibraryCatalogService componentLibraryCatalogService;

    public SpanService(ModuleManager moduleManager) {
        this.segmentDAO = moduleManager.find(StorageModule.NAME).getService(ISegmentUIDAO.class);
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        this.componentLibraryCatalogService = moduleManager.find(ConfigurationModule.NAME).getService(IComponentLibraryCatalogService.class);
    }

    public JsonObject load(String segmentId, int spanId) {
        TraceSegmentObject segmentObject = segmentDAO.load(segmentId);

        JsonObject spanJson = new JsonObject();
        List<SpanObject> spans = segmentObject.getSpansList();
        for (SpanObject spanObject : spans) {
            if (spanId == spanObject.getSpanId()) {
                String operationName = spanObject.getOperationName();
                if (spanObject.getOperationNameId() != 0) {
                    ServiceName serviceName = serviceNameCacheService.get(spanObject.getOperationNameId());
                    if (StringUtils.isNotEmpty(serviceName)) {
                        operationName = serviceName.getServiceName();
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
                    componentJson.addProperty("value", this.componentLibraryCatalogService.getComponentName(spanObject.getComponentId()));
                }
                tagsArray.add(componentJson);

                JsonObject peerJson = new JsonObject();
                peerJson.addProperty("key", "peer");
                if (spanObject.getPeerId() == 0) {
                    peerJson.addProperty("value", spanObject.getPeer());
                } else {
                    peerJson.addProperty("value", applicationCacheService.getApplicationById(spanObject.getPeerId()).getApplicationCode());
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
