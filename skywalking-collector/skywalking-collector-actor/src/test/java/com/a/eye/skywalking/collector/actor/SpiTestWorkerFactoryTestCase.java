package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author pengys5
 */
public class SpiTestWorkerFactoryTestCase {

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

    @Test
    public void testWorkerCreate() {


        SpiTestWorkerFactory factory = Mockito.mock(SpiTestWorkerFactory.class);
        Mockito.when(factory.workerName()).thenReturn("");
        factory.createWorker(system);
    }
}
