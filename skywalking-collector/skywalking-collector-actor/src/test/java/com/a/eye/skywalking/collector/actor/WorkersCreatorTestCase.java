package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author pengys5
 */
public class WorkersCreatorTestCase {

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
    public void testBoot() {
        WorkersCreator.INSTANCE.boot(system);
    }
}
