package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.CollectorSystem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author pengys5
 */
public class TestClusterWorkerTestCase {

    private CollectorSystem collectorSystem;

//    @Before
    public void createSystem() throws Exception {
        collectorSystem = new CollectorSystem();
        collectorSystem.boot();
    }

//    @Before
    public void terminateSystem() {
        collectorSystem.terminate();
    }

//    @Test
    public void testTellWorker() throws Exception {
        WorkerRefs workerRefs = collectorSystem.getClusterContext().lookup(TestClusterWorker.TestClusterWorkerRole.INSTANCE);
        workerRefs.tell("Print");
        workerRefs.tell("TellLocalWorker");
        workerRefs.tell("TellLocalAsyncWorker");

        Thread.sleep(5000);
    }
}
