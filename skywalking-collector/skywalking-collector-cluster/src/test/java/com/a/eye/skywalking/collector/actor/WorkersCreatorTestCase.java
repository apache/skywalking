package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
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
        new JavaTestKit(system) {{
            WorkersCreator.INSTANCE.boot(system);

            system.actorSelection("/user/SpiTestWorker_1").tell("Test1", getRef());
            expectMsgEquals(duration("1 second"), "Yes");
            system.actorSelection("/user/SpiTestWorker_2").tell("Test2", getRef());
            expectMsgEquals(duration("1 second"), "No");
        }};
    }
}
