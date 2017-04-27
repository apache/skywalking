package org.skywalking.apm.sniffer.mock.context;

import org.skywalking.apm.trace.TraceSegment;

/**
 * The <code>SegmentAssert</code> interface should be implemented by any
 * class whose instances are intended to assert a trace segment data.
 * <p>
 * Created by wusheng on 2017/2/20.
 */
public interface SegmentAssert {
    void call(TraceSegment finishedSegment);
}
