package com.a.eye.skywalking.collector.cluster;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.pattern.Patterns;
import akka.testkit.TestActorRef;
import com.a.eye.skywalking.collector.actor.WorkerRef;
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
    TestActorRef<WorkersListener> senderActorRef;
    TestActorRef<WorkersListener> receiveactorRef;

    @Before
    public void initData() {
        system = ActorSystem.create();

        final Props props = Props.create(WorkersListener.class);
        senderActorRef = TestActorRef.create(system, props, "WorkersListenerSender");
        receiveactorRef = TestActorRef.create(system, props, "WorkersListenerReceive");

        WorkerListenerMessage.RegisterMessage message = new WorkerListenerMessage.RegisterMessage("WorkersListener");
        receiveactorRef.tell(message, senderActorRef);
    }

    @After
    public void terminateSystem() throws IllegalAccessException {
        system.terminate();
        system.awaitTermination();
        system = null;
        MemberModifier.field(WorkersRefCenter.class, "roleToWorkerRef").set(WorkersRefCenter.INSTANCE, new ConcurrentHashMap());
        MemberModifier.field(WorkersRefCenter.class, "actorRefToWorkerRef").set(WorkersRefCenter.INSTANCE, new ConcurrentHashMap());
    }

    @Test
    public void testRegister() throws IllegalAccessException {
        Map<ActorRef, WorkerRef> actorRefToWorkerRef = (Map<ActorRef, WorkerRef>) MemberModifier.field(WorkersRefCenter.class, "actorRefToWorkerRef").get(WorkersRefCenter.INSTANCE);
        ActorRef senderRefInWorkerRef = (ActorRef) MemberModifier.field(WorkerRef.class, "actorRef").get(actorRefToWorkerRef.get(senderActorRef));
        Assert.assertEquals(senderActorRef, senderRefInWorkerRef);

        Map<String, List<WorkerRef>> roleToWorkerRef = (Map<String, List<WorkerRef>>) MemberModifier.field(WorkersRefCenter.class, "roleToWorkerRef").get(WorkersRefCenter.INSTANCE);
        WorkerRef workerRef = roleToWorkerRef.get("WorkersListener").get(0);
        senderRefInWorkerRef = (ActorRef) MemberModifier.field(WorkerRef.class, "actorRef").get(workerRef);
        Assert.assertEquals(senderActorRef, senderRefInWorkerRef);
    }
}
