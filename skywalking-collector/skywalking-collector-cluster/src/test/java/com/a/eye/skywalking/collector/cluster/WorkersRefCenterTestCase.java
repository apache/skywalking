package com.a.eye.skywalking.collector.cluster;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActorRef;
import com.a.eye.skywalking.collector.actor.WorkerRef;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.api.support.membermodification.MemberModifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author pengys5
 */
public class WorkersRefCenterTestCase {

    ActorSystem system;
    TestActorRef<WorkersListener> actorRef1;
    TestActorRef<WorkersListener> actorRef2;
    TestActorRef<WorkersListener> actorRef3;

    @Before
    public void createSystem() {
        system = ActorSystem.create();
        final Props props = Props.create(WorkersListener.class);
        actorRef1 = TestActorRef.create(system, props, "WorkersListener1");
        actorRef2 = TestActorRef.create(system, props, "WorkersListener2");
        actorRef3 = TestActorRef.create(system, props, "WorkersListener3");

        WorkersRefCenter.INSTANCE.register(actorRef1, "WorkersListener");
        WorkersRefCenter.INSTANCE.register(actorRef2, "WorkersListener");
        WorkersRefCenter.INSTANCE.register(actorRef3, "WorkersListener");
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
        Map<String, List<WorkerRef>> roleToActor = (Map<String, List<WorkerRef>>) MemberModifier.field(WorkersRefCenter.class, "roleToWorkerRef").get(WorkersRefCenter.INSTANCE);
        List<WorkerRef> workerRefs = roleToActor.get("WorkersListener");

        ActorRef actorRef = (ActorRef) MemberModifier.field(WorkerRef.class, "actorRef").get(workerRefs.get(0));
        Assert.assertEquals(actorRef1, actorRef);
        actorRef = (ActorRef) MemberModifier.field(WorkerRef.class, "actorRef").get(workerRefs.get(1));
        Assert.assertEquals(actorRef2, actorRef);
        actorRef = (ActorRef) MemberModifier.field(WorkerRef.class, "actorRef").get(workerRefs.get(2));
        Assert.assertEquals(actorRef3, actorRef);

        Map<ActorRef, WorkerRef> actorToRole = (Map<ActorRef, WorkerRef>) MemberModifier.field(WorkersRefCenter.class, "actorRefToWorkerRef").get(WorkersRefCenter.INSTANCE);
        Assert.assertEquals("WorkersListener", actorToRole.get(actorRef1).getWorkerRole());
        Assert.assertEquals("WorkersListener", actorToRole.get(actorRef2).getWorkerRole());
        Assert.assertEquals("WorkersListener", actorToRole.get(actorRef3).getWorkerRole());
    }

    @Test
    public void testUnRegister() throws IllegalAccessException {
        WorkersRefCenter.INSTANCE.unregister(actorRef1);

        Map<String, List<WorkerRef>> roleToWorkerRef = (Map<String, List<WorkerRef>>) MemberModifier.field(WorkersRefCenter.class, "roleToWorkerRef").get(WorkersRefCenter.INSTANCE);
        ActorRef actorRef = (ActorRef) MemberModifier.field(WorkerRef.class, "actorRef").get(roleToWorkerRef.get("WorkersListener").get(0));
        Assert.assertEquals(actorRef2, actorRef);

        actorRef = (ActorRef) MemberModifier.field(WorkerRef.class, "actorRef").get(roleToWorkerRef.get("WorkersListener").get(1));
        Assert.assertEquals(actorRef3, actorRef);

        Map<ActorRef, WorkerRef> actorRefToWorkerRef = (Map<ActorRef, WorkerRef>) MemberModifier.field(WorkersRefCenter.class, "actorRefToWorkerRef").get(WorkersRefCenter.INSTANCE);
        Assert.assertEquals(null, actorRefToWorkerRef.get(actorRef1));
    }

    @Test
    public void testSizeOf() throws NoAvailableWorkerException {
        Assert.assertEquals(3, WorkersRefCenter.INSTANCE.availableWorks("WorkersListener").size());
    }
}
