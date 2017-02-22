package com.a.eye.skywalking.collector.cluster;

import com.a.eye.skywalking.sniffer.mock.trace.TraceSegmentBuilderFactory;
import com.a.eye.skywalking.trace.TraceSegment;
import org.junit.Test;

/**
 * Created by pengys5 on 2017/2/22 0022.
 */
public class TraceSegmentTestCase {

    @Test
    public void testProducerSend() {
        TraceSegment traceSegment = TraceSegmentBuilderFactory.INSTANCE.singleTomcat200Trace();
    }
}
