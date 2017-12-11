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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostUIDAO;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentCostTable;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTraceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentTopService {

    private final Logger logger = LoggerFactory.getLogger(SegmentTopService.class);

    private final IGlobalTraceUIDAO globalTraceDAO;
    private final ISegmentCostUIDAO segmentCostDAO;

    public SegmentTopService(ModuleManager moduleManager) {
        this.globalTraceDAO = moduleManager.find(StorageModule.NAME).getService(IGlobalTraceUIDAO.class);
        this.segmentCostDAO = moduleManager.find(StorageModule.NAME).getService(ISegmentCostUIDAO.class);
    }

    public JsonObject loadTop(long startTime, long endTime, long minCost, long maxCost, String operationName,
        String globalTraceId, ISegmentCostUIDAO.Error error, int applicationId, int limit, int from,
        ISegmentCostUIDAO.Sort sort) {
        logger.debug("startTime: {}, endTime: {}, minCost: {}, maxCost: {}, operationName: {}, globalTraceId: {}, error: {}, applicationId: {}, limit: {}, from: {}", startTime, endTime, minCost, maxCost, operationName, globalTraceId, error, applicationId, limit, from);

        List<String> segmentIds = new LinkedList<>();
        if (StringUtils.isNotEmpty(globalTraceId)) {
            segmentIds = globalTraceDAO.getSegmentIds(globalTraceId);
        }

        JsonObject loadTopJsonObj = segmentCostDAO.loadTop(startTime, endTime, minCost, maxCost, operationName, error, applicationId, segmentIds, limit, from, sort);
        JsonArray loadTopJsonArray = loadTopJsonObj.get("data").getAsJsonArray();
        for (JsonElement loadTopElement : loadTopJsonArray) {
            JsonObject jsonObject = loadTopElement.getAsJsonObject();
            String segmentId = jsonObject.get(SegmentCostTable.COLUMN_SEGMENT_ID).getAsString();
            List<String> globalTraces = globalTraceDAO.getGlobalTraceId(segmentId);
            if (CollectionUtils.isNotEmpty(globalTraces)) {
                jsonObject.addProperty(GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID, globalTraces.get(0));
            }
        }
        return loadTopJsonObj;
    }
}
