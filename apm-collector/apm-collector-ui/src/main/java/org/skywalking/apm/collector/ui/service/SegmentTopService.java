package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonObject;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.dao.ISegmentCostDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class SegmentTopService {

    private final Logger logger = LoggerFactory.getLogger(SegmentTopService.class);

    public JsonObject loadTop(long startTime, long endTime, long minCost, long maxCost, String operationName,
        String globalTraceId, int limit, int from, ISegmentCostDAO.Sort sort) {
        logger.debug("startTime: {}, endTime: {}, minCost: {}, maxCost: {}, operationName: {}, globalTraceId: {}, limit: {}, from: {}", startTime, endTime, minCost, maxCost, operationName, globalTraceId, limit, from);
        ISegmentCostDAO segmentCostDAO = (ISegmentCostDAO)DAOContainer.INSTANCE.get(ISegmentCostDAO.class.getName());
        return segmentCostDAO.loadTop(startTime, endTime, minCost, maxCost, operationName, globalTraceId, limit, from, sort);
    }
}
