package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.lmax.disruptor.RingBuffer;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
public class AbstractLocalAsyncWorkerTestCase {

    @Test
    public void testAllocateJob() throws Exception {
        AbstractLocalAsyncWorker worker = mock(AbstractLocalAsyncWorker.class);

        String message = "Test";
        worker.allocateJob(message);
        verify(worker).onWork(message);
    }

    @Test
    public void testOnEventWhenNotEnd() throws Exception {
        AbstractLocalAsyncWorker worker = mock(AbstractLocalAsyncWorker.class);

        AbstractLocalAsyncWorker.WorkerWithDisruptor disruptor = new AbstractLocalAsyncWorker.WorkerWithDisruptor(null, worker);

        MessageHolder holder = new MessageHolder();
        String message = "Test";
        holder.setMessage(message);
        disruptor.onEvent(holder, 0, false);

        verify(worker).onWork(message);
    }

    @Test
    public void testOnEventWhenEnd() throws Exception {
        AbstractLocalAsyncWorker worker = mock(AbstractLocalAsyncWorker.class);

        AbstractLocalAsyncWorker.WorkerWithDisruptor disruptor = new AbstractLocalAsyncWorker.WorkerWithDisruptor(null, worker);

        MessageHolder holder = new MessageHolder();
        String message = "Test";
        holder.setMessage(message);
        disruptor.onEvent(holder, 0, true);

        verify(worker, times(1)).onWork(message);
        verify(worker, times(1)).onWork(argThat(new IsEndOfBatchCommandClass()));
    }

    class IsEndOfBatchCommandClass extends ArgumentMatcher<EndOfBatchCommand> {
        public boolean matches(Object para) {
            return para.getClass() == EndOfBatchCommand.class;
        }
    }

}
