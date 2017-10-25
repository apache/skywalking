/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonObject;
import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.dao.IGlobalTraceDAO;
import org.skywalking.apm.collector.ui.dao.ISegmentCostDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentTopService {

    private final Logger logger = LoggerFactory.getLogger(SegmentTopService.class);

    public JsonObject loadTop(long startTime, long endTime, long minCost, long maxCost, String operationName,
        String globalTraceId, ISegmentCostDAO.Error error, int applicationId, int limit, int from,
        ISegmentCostDAO.Sort sort) {
        logger.debug("startTime: {}, endTime: {}, minCost: {}, maxCost: {}, operationName: {}, globalTraceId: {}, error: {}, applicationId: {}, limit: {}, from: {}", startTime, endTime, minCost, maxCost, operationName, globalTraceId, error, applicationId, limit, from);

        List<String> segmentIds = new LinkedList<>();
        if (StringUtils.isNotEmpty(globalTraceId)) {
            IGlobalTraceDAO globalTraceDAO = (IGlobalTraceDAO)DAOContainer.INSTANCE.get(IGlobalTraceDAO.class.getName());
            segmentIds = globalTraceDAO.getSegmentIds(globalTraceId);
        }
        ISegmentCostDAO segmentCostDAO = (ISegmentCostDAO)DAOContainer.INSTANCE.get(ISegmentCostDAO.class.getName());
        return segmentCostDAO.loadTop(startTime, endTime, minCost, maxCost, operationName, error, applicationId, segmentIds, limit, from, sort);
    }
}
