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
import org.skywalking.apm.collector.storage.dao.IGlobalTraceUIDAO;
import org.skywalking.apm.collector.storage.dao.ISegmentCostUIDAO;
import org.skywalking.apm.collector.storage.service.DAOService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentTopService {

    private final Logger logger = LoggerFactory.getLogger(SegmentTopService.class);

    private final DAOService daoService;
    private final CacheServiceManager cacheServiceManager;

    public SegmentTopService(DAOService daoService, CacheServiceManager cacheServiceManager) {
        this.daoService = daoService;
        this.cacheServiceManager = cacheServiceManager;
    }

    public JsonObject loadTop(long startTime, long endTime, long minCost, long maxCost, String operationName,
        String globalTraceId, ISegmentCostUIDAO.Error error, int applicationId, int limit, int from,
        ISegmentCostUIDAO.Sort sort) {
        logger.debug("startTime: {}, endTime: {}, minCost: {}, maxCost: {}, operationName: {}, globalTraceId: {}, error: {}, applicationId: {}, limit: {}, from: {}", startTime, endTime, minCost, maxCost, operationName, globalTraceId, error, applicationId, limit, from);

        List<String> segmentIds = new LinkedList<>();
        if (StringUtils.isNotEmpty(globalTraceId)) {
            IGlobalTraceUIDAO globalTraceDAO = (IGlobalTraceUIDAO)daoService.get(IGlobalTraceUIDAO.class);
            segmentIds = globalTraceDAO.getSegmentIds(globalTraceId);
        }
        ISegmentCostUIDAO segmentCostDAO = (ISegmentCostUIDAO)daoService.get(ISegmentCostUIDAO.class);
        return segmentCostDAO.loadTop(startTime, endTime, minCost, maxCost, operationName, error, applicationId, segmentIds, limit, from, sort);
    }
}
