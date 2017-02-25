package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
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
    public void testCreateWorkerWhenWorkNameIsNull() {
        AbstractWorkerProvider aWorkerProvider = new AbstractWorkerProvider() {
            @Override
            public String workerRole() {
                return null;
            }

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
    public void testCreateWorkerWhenWorkerClassIsNull() {
        AbstractWorkerProvider aWorkerProvider = new AbstractWorkerProvider() {
            @Override
            public String workerRole() {
                return "Test";
            }

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
            public String workerRole() {
                return "Test";
            }

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
