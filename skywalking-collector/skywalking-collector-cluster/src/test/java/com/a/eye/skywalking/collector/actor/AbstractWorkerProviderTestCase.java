package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author pengys5
 */
public class AbstractWorkerProviderTestCase {
    ActorSystem system;

    @Before
    public void createSystem() {
        system = ActorSystem.create();
    }

    @After
    public void terminateSystem() throws IllegalAccessException {
        system.terminate();
        system.awaitTermination();
        system = null;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWorkerWhenWorkerClassIsNull() {
        AbstractWorkerProvider aWorkerProvider = new AbstractWorkerProvider() {
            @Override
            public Class workerClass() {
                return Object.class;
            }

            @Override
            public int workerNum() {
                return 1;
            }
        };

        aWorkerProvider.createWorker(system);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWorkerWhenWorkerNumLessThan_1() {
        AbstractWorkerProvider aWorkerProvider = new AbstractWorkerProvider() {

            @Override
            public Class workerClass() {
                return null;
            }

            @Override
            public int workerNum() {
                return 0;
            }
        };

        aWorkerProvider.createWorker(system);
    }
}
