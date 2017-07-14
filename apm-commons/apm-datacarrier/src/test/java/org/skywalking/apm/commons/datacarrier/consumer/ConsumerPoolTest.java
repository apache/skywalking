package org.skywalking.apm.commons.datacarrier.consumer;

import org.junit.Assert;
import org.junit.Test;
import org.powermock.api.support.membermodification.MemberModifier;
import org.skywalking.apm.commons.datacarrier.SampleData;
import org.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;
import org.skywalking.apm.commons.datacarrier.buffer.Channels;
import org.skywalking.apm.commons.datacarrier.partition.SimpleRollingPartitioner;

/**
 * Created by wusheng on 2016/10/26.
 */
public class ConsumerPoolTest {
    @Test
    public void testBeginConsumerPool() throws IllegalAccessException {
        Channels<SampleData> channels = new Channels<SampleData>(2, 100, new SimpleRollingPartitioner<SampleData>(), BufferStrategy.BLOCKING);
        ConsumerPool<SampleData> pool = new ConsumerPool<SampleData>(channels, new SampleConsumer(), 2);
        pool.begin();

        ConsumerThread[] threads = (ConsumerThread[])MemberModifier.field(ConsumerPool.class, "consumerThreads").get(pool);
        Assert.assertEquals(2, threads.length);
        Assert.assertTrue(threads[0].isAlive());
        Assert.assertTrue(threads[1].isAlive());
    }

    @Test
    public void testCloseConsumerPool() throws InterruptedException, IllegalAccessException {
        Channels<SampleData> channels = new Channels<SampleData>(2, 100, new SimpleRollingPartitioner<SampleData>(), BufferStrategy.BLOCKING);
        ConsumerPool<SampleData> pool = new ConsumerPool<SampleData>(channels, new SampleConsumer(), 2);
        pool.begin();

        Thread.sleep(5000);
        pool.close();
        ConsumerThread[] threads = (ConsumerThread[])MemberModifier.field(ConsumerPool.class, "consumerThreads").get(pool);

        Assert.assertEquals(2, threads.length);
        Assert.assertFalse((Boolean)MemberModifier.field(ConsumerThread.class, "running").get(threads[0]));
        Assert.assertFalse((Boolean)MemberModifier.field(ConsumerThread.class, "running").get(threads[1]));
    }
}
