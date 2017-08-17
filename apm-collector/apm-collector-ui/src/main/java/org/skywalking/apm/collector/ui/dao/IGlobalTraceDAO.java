package org.skywalking.apm.collector.ui.dao;

import java.util.List;

/**
 * @author pengys5
 */
public interface IGlobalTraceDAO {
    List<String> getGlobalTraceId(String segmentId);

    List<String> getSegmentIds(String globalTraceId);
}
