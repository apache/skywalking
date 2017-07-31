package org.skywalking.apm.collector.ui.dao;

import org.skywalking.apm.network.proto.TraceSegmentObject;

/**
 * @author pengys5
 */
public interface ISegmentDAO {
    TraceSegmentObject load(String segmentId);
}
