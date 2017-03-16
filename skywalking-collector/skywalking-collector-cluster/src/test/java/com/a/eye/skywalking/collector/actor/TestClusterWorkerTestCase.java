package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.CollectorSystem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author pengys5
 */
public class TestClusterWorkerTestCase {

    @Before
    public void createSystem() throws Exception {
        CollectorSystem.INSTANCE.boot();
    }

    @After
    public void terminateSystem() {
        CollectorSystem.INSTANCE.terminate();
    }

    @Test
    public void testTellWorker() throws Exception {
        WorkerRefs workerRefs = CollectorSystem.INSTANCE.getClusterContext().lookup(TestClusterWorker.TestClusterWorkerRole.INSTANCE);
        workerRefs.tell("Print");
        workerRefs.tell("TellLocalWorker");
        workerRefs.tell("TellLocalAsyncWorker");

        Thread.sleep(5000);
    }
}
