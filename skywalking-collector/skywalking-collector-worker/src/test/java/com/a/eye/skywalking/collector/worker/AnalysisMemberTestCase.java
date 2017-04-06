package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Mockito.*;

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

    @Test
    public void testOnWorkException() throws Exception {
        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        TestAnalysisMember member = PowerMockito.spy(new TestAnalysisMember(TestAnalysisMember.Role.INSTANCE, clusterWorkerContext, localWorkerContext));

        doThrow(new TestException()).when(member).analyse(anyObject());

        ExceptionAnswer answer = new ExceptionAnswer();
        PowerMockito.when(member, "saveException", any(TestException.class)).thenAnswer(answer);

        member.onWork(new Object());

        Assert.assertEquals(true, answer.isTestException);
    }

    class TestException extends Exception {

    }


    class ExceptionAnswer implements Answer {

        boolean isTestException = false;

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            Object obj = invocation.getArguments()[0];
            if (obj instanceof TestException) {
                isTestException = true;
            } else {
                isTestException = false;
            }
            return null;
        }
    }
}
