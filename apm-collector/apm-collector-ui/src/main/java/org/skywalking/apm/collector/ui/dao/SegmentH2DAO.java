package org.skywalking.apm.collector.ui.dao;

import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.network.proto.TraceSegmentObject;

/**
 * @author pengys5
 */
public class SegmentH2DAO extends H2DAO implements ISegmentDAO {
    @Override public TraceSegmentObject load(String segmentId) {
        return null;
    }
}
