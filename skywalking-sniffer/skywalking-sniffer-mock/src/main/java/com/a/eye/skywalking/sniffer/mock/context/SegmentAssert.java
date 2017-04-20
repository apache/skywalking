package com.a.eye.skywalking.sniffer.mock.context;

import com.a.eye.skywalking.trace.TraceSegment;

/**
 * The <code>SegmentAssert</code> interface should be implemented by any
 * class whose instances are intended to assert a trace segment data.
 *
 * Created by wusheng on 2017/2/20.
 */
public interface SegmentAssert {
    void call(TraceSegment finishedSegment);
}
