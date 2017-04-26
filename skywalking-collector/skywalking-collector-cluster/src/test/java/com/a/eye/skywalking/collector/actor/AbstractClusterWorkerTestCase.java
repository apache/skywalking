package com.a.eye.skywalking.collector.actor;

import akka.actor.Address;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ClusterEvent.MemberUp.class, Address.class})
public class AbstractClusterWorkerTestCase {

    private AbstractClusterWorker.WorkerWithAkka workerWithAkka = mock(AbstractClusterWorker.WorkerWithAkka.class, CALLS_REAL_METHODS);
    private AbstractClusterWorker worker = PowerMockito.spy(new Impl(WorkerRole.INSTANCE, null, null));

    @Before
    public void init(){
        Logger logger = mock(Logger.class);
        Whitebox.setInternalState(workerWithAkka, "logger", logger);
        Whitebox.setInternalState(workerWithAkka, "ownerWorker", worker);
    }

    @Test
    public void testAllocateJob() throws Exception {

        String jobStr = "TestJob";
        worker.allocateJob(jobStr);

        verify(worker).onWork(jobStr);
    }

    @Test
    public void testMemberUp() throws Throwable {
        ClusterEvent.MemberUp memberUp = mock(ClusterEvent.MemberUp.class);

        Address address = mock(Address.class);
        when(address.toString()).thenReturn("address");

        Member member = mock(Member.class);
        when(member.address()).thenReturn(address);

        when(memberUp.member()).thenReturn(member);

        workerWithAkka.onReceive(memberUp);

        verify(workerWithAkka).register(member);
    }

    @Test
    public void testMessage() throws Throwable {
        String message = "test";
        workerWithAkka.onReceive(message);

        verify(worker).allocateJob(message);
    }

    static class Impl extends AbstractClusterWorker {
        @Override public void preStart() throws ProviderNotFoundException {
        }

        public Impl(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
            super(role, clusterContext, selfContext);
        }

        @Override protected void onWork(Object message) throws Exception {

        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return Impl.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
