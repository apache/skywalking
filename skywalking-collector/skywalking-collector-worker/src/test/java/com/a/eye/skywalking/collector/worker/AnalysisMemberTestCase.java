package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import org.junit.Test;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
public class AnalysisMemberTestCase {

    @Test
    public void testCommandOnWork() throws Exception {
        AnalysisMember member = mock(AnalysisMember.class);

        EndOfBatchCommand command = new EndOfBatchCommand();
        member.onWork(command);
        verify(member, times(1)).aggregation();
        verify(member, never()).analyse(anyObject());
    }

    @Test
    public void testAnalyse() throws Exception {
        AnalysisMember member = mock(AnalysisMember.class);

        Object message = new Object();
        member.onWork(message);
        verify(member, never()).aggregation();
        verify(member, times(1)).analyse(anyObject());
    }
}
