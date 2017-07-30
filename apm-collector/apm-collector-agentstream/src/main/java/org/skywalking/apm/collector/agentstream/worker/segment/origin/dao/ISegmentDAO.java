package org.skywalking.apm.collector.agentstream.worker.segment.origin.dao;

import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;

/**
 * @author pengys5
 */
public interface ISegmentDAO {
    List<?> prepareBatch(Map<String, Data> dataMap);
}
