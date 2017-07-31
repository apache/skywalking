package org.skywalking.apm.collector.ui.dao;

import java.util.List;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class GlobalTraceH2DAO extends H2DAO implements IGlobalTraceDAO {
    @Override public List<String> getGlobalTraceId(String segmentId) {
        return null;
    }

    @Override public List<String> getSegmentIds(String globalTraceId) {
        return null;
    }
}
