package com.a.eye.skywalking.api.sampling;

import com.a.eye.skywalking.api.boot.ServiceManager;
import com.a.eye.skywalking.api.conf.Config;
import com.a.eye.skywalking.trace.TraceSegment;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author wusheng
 */
public class SamplingServiceTest {
    @Test
    public void test50Percent(){
        Config.Agent.SAMPLING_CYCLE = 2;
        ServiceManager.INSTANCE.boot();

        TraceSegment segment = new TraceSegment();
        Assert.assertTrue(segment.isSampled());

        SamplingService service = ServiceManager.INSTANCE.findService(SamplingService.class);
        service.trySampling(segment);
        Assert.assertFalse(segment.isSampled());

        segment = new TraceSegment();
        service.trySampling(segment);
        Assert.assertTrue(segment.isSampled());

        segment = new TraceSegment();
        service.trySampling(segment);
        Assert.assertFalse(segment.isSampled());
    }

    @AfterClass
    public static void clear(){
        Config.Agent.SAMPLING_CYCLE = 1;
        ServiceManager.INSTANCE.boot();
    }
}
