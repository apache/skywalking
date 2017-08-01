package org.skywalking.apm.collector.agentstream.worker.segment.cost.dao;

import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;

/**
 * @author pengys5
 */
public interface ISegmentCostDAO {
    List<?> prepareBatch(Map<String, Data> dataMap);
}
