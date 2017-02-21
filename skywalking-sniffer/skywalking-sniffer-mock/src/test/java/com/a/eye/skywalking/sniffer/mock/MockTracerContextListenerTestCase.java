package com.a.eye.skywalking.sniffer.mock;

import com.a.eye.skywalking.sniffer.mock.context.MockTracerContextListener;
import com.a.eye.skywalking.sniffer.mock.trace.TraceSegmentBuilderFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/21.
 */
public class MockTracerContextListenerTestCase {
    @Test
    public void testAfterFinished(){
        MockTracerContextListener listener = new MockTracerContextListener();
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat200Trace());

        Assert.assertNotNull(listener.getFinished(0));
    }
}
