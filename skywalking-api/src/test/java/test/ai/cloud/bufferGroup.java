package test.ai.cloud;

import com.ai.cloud.skywalking.buffer.BufferGroup;
import com.ai.cloud.skywalking.conf.Config;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class bufferGroup {

    @Test
    public void checkConsumerWorkerIsStartIfConsumerSizeIsZero() {
        Config.Consumer.MAX_CONSUMER = 0;
        BufferGroup bufferGroup = new BufferGroup("testBufferGroup");
        int count = getConsumerWorkerThreadCount();
        assertEquals(Config.Consumer.MAX_CONSUMER, count);
    }

    private int getConsumerWorkerThreadCount() {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        ThreadGroup topGroup = group;
        while (group != null) {
            topGroup = group;
            group = group.getParent();
        }

        int activeCount = topGroup.activeCount();
        Thread[] threads = new Thread[activeCount];
        topGroup.enumerate(threads);
        int count = 0;
        for (Thread thread : threads) {
            if (thread != null && "ConsumerWorker".equals(thread.getName())) {
                count++;
            }
        }
        return count;
    }
}
