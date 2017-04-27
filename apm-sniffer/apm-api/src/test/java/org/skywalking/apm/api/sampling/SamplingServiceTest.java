package org.skywalking.apm.api.sampling;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.api.boot.ServiceManager;
import org.skywalking.apm.api.conf.Config;
import org.skywalking.apm.trace.TraceSegment;

/**
 * @author wusheng
 */
public class SamplingServiceTest {
    @Test
    public void test50Percent() {
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
    public static void clear() {
        Config.Agent.SAMPLING_CYCLE = 1;
        ServiceManager.INSTANCE.boot();
    }
}
