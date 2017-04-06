package com.a.eye.skywalking.collector.actor;

import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;

/**
 * @author pengys5
 */
public class AbstractClusterWorkerTestCase {

    @Test
    public void testAllocateJob() throws Exception {
        AbstractClusterWorker worker = PowerMockito.mock(AbstractClusterWorker.class);

        String jobStr = "TestJob";
        worker.allocateJob(jobStr);

        Mockito.verify(worker).onWork(jobStr);
    }
}
