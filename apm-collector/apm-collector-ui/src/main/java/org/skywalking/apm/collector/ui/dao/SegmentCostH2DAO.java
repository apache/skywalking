package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonObject;
import java.util.List;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class SegmentCostH2DAO extends H2DAO implements ISegmentCostDAO {
    @Override public JsonObject loadTop(long startTime, long endTime, long minCost, long maxCost, String operationName,
        List<String> segmentIds, int limit, int from, Sort sort) {
        return null;
    }
}
