package com.a.eye.skywalking.collector.cluster;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.pattern.Patterns;
import akka.testkit.TestActorRef;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.api.support.membermodification.MemberModifier;
import scala.concurrent.Future;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author pengys5
 */
public class WorkerListenerTestCase {

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
        MemberModifier.field(WorkersRefCenter.class, "actorToRole").set(WorkersRefCenter.INSTANCE, new ConcurrentHashMap());
        MemberModifier.field(WorkersRefCenter.class, "roleToActor").set(WorkersRefCenter.INSTANCE, new ConcurrentHashMap());
    }

    @Test
    public void testRegister() throws IllegalAccessException {
        final Props props = Props.create(WorkersListener.class);
        final TestActorRef<WorkersListener> senderActorRef = TestActorRef.create(system, props, "WorkersListenerSender");
        final TestActorRef<WorkersListener> receiveactorRef = TestActorRef.create(system, props, "WorkersListenerReceive");

        WorkerListenerMessage.RegisterMessage message = new WorkerListenerMessage.RegisterMessage("WorkersListener");
        receiveactorRef.tell(message, senderActorRef);

        Map<ActorRef, String> actorToRole = (Map<ActorRef, String>) MemberModifier.field(WorkersRefCenter.class, "actorToRole").get(WorkersRefCenter.INSTANCE);
        Assert.assertEquals("WorkersListener", actorToRole.get(senderActorRef));

        Map<String, List<ActorRef>> roleToActor = (Map<String, List<ActorRef>>) MemberModifier.field(WorkersRefCenter.class, "roleToActor").get(WorkersRefCenter.INSTANCE);
        ActorRef[] actorRefs = {senderActorRef};
        Assert.assertArrayEquals(actorRefs, roleToActor.get("WorkersListener").toArray());
    }

    @Test
    public void testTerminated() throws IllegalAccessException {
        final Props props = Props.create(WorkersListener.class);
        final TestActorRef<WorkersListener> senderActorRef = TestActorRef.create(system, props, "WorkersListenerSender");
        final TestActorRef<WorkersListener> receiveactorRef = TestActorRef.create(system, props, "WorkersListenerReceive");

        WorkerListenerMessage.RegisterMessage message = new WorkerListenerMessage.RegisterMessage("WorkersListener");
        receiveactorRef.tell(message, senderActorRef);

        senderActorRef.stop();

        Map<ActorRef, String> actorToRole = (Map<ActorRef, String>) MemberModifier.field(WorkersRefCenter.class, "actorToRole").get(WorkersRefCenter.INSTANCE);
        Assert.assertEquals(null, actorToRole.get(senderActorRef));

        Map<String, List<ActorRef>> roleToActor = (Map<String, List<ActorRef>>) MemberModifier.field(WorkersRefCenter.class, "roleToActor").get(WorkersRefCenter.INSTANCE);
        ActorRef[] actorRefs = {};
        Assert.assertArrayEquals(actorRefs, roleToActor.get("WorkersListener").toArray());
    }

    @Test
    public void testUnhandled() {

    }
}
