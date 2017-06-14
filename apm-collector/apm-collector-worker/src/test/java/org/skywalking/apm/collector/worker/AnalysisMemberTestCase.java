package org.skywalking.apm.collector.worker;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.queue.EndOfBatchCommand;

import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(TestAnalysisMember.class)
@PowerMockIgnore({"javax.management.*"})
public class AnalysisMemberTestCase {

    @Test
    public void testCommandOnWork() throws Exception {
        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        TestAnalysisMember member = PowerMockito.spy(new TestAnalysisMember(TestAnalysisMember.Role.INSTANCE, clusterWorkerContext, localWorkerContext));

        EndOfBatchCommand command = new EndOfBatchCommand();
        member.onWork(command);
        verify(member, times(1)).aggregation();
        verify(member, never()).analyse(anyObject());
    }

    @Test
    public void testAnalyse() throws Exception {
        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        TestAnalysisMember member = PowerMockito.spy(new TestAnalysisMember(TestAnalysisMember.Role.INSTANCE, clusterWorkerContext, localWorkerContext));

        Object message = new Object();
        member.onWork(message);
        verify(member, never()).aggregation();
        verify(member, times(1)).analyse(anyObject());
    }

    @Test
    public void testPreStart() throws Exception {
        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        TestAnalysisMember member = PowerMockito.spy(new TestAnalysisMember(TestAnalysisMember.Role.INSTANCE, clusterWorkerContext, localWorkerContext));
        member.preStart();
    }
}
