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
 * @author pengys5
 */
public class SegmentTopService {

    private final Logger logger = LoggerFactory.getLogger(SegmentTopService.class);

    public JsonObject loadTop(long startTime, long endTime, long minCost, long maxCost, String operationName,
        String globalTraceId, int limit, int from, ISegmentCostDAO.Sort sort) {
        logger.debug("startTime: {}, endTime: {}, minCost: {}, maxCost: {}, operationName: {}, globalTraceId: {}, limit: {}, from: {}", startTime, endTime, minCost, maxCost, operationName, globalTraceId, limit, from);

        List<String> segmentIds = new LinkedList<>();
        if (StringUtils.isNotEmpty(globalTraceId)) {
            IGlobalTraceDAO globalTraceDAO = (IGlobalTraceDAO)DAOContainer.INSTANCE.get(IGlobalTraceDAO.class.getName());
            segmentIds = globalTraceDAO.getSegmentIds(globalTraceId);
        }
        ISegmentCostDAO segmentCostDAO = (ISegmentCostDAO)DAOContainer.INSTANCE.get(ISegmentCostDAO.class.getName());
        return segmentCostDAO.loadTop(startTime, endTime, minCost, maxCost, operationName, segmentIds, limit, from, sort);
    }
}
