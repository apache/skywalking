package com.a.eye.skywalking.collector.cluster;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.a.eye.skywalking.collector.cluster.manager.ActorCreator;
import com.a.eye.skywalking.collector.cluster.manager.ActorManagerActor;
import org.junit.Test;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
public class ActorCreatorTestCase {

    @Test
    public void testCreate() {
        ActorSystem system = mock(ActorSystem.class);
//        ActorCreator.INSTANCE.create(system, ActorManagerActor.class, 1);
//        verify(system).actorOf(Props.create(ActorManagerActor.class), "ActorManagerActor");
    }
}
