package com.a.eye.skywalking.collector.cluster;

import akka.actor.ActorSystem;
import com.a.eye.skywalking.collector.cluster.manager.ActorManagerActorFactory;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author pengys5
 */
public class ActorProviderTestCase {

    @Test
    public void testActorName() {
        ActorManagerActorFactory factory = new ActorManagerActorFactory();
        String actorName = factory.actorName();
        Assert.assertEquals("ActorManagerActor", actorName);
    }

    @Test
    public void testCreateActor() {
        ActorSystem system = Mockito.mock(ActorSystem.class);
        ActorManagerActorFactory factory = new ActorManagerActorFactory();
        factory.createActor(system);
    }
}
