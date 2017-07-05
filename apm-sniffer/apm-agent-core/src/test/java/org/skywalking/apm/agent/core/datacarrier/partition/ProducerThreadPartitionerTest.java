package org.skywalking.apm.agent.core.datacarrier.partition;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.agent.core.datacarrier.SampleData;

/**
 * Created by wusheng on 2016/10/25.
 */
public class ProducerThreadPartitionerTest {
    @Test
    public void testPartition(){
        int partitionNum = (int)Thread.currentThread().getId() % 10;
        ProducerThreadPartitioner<SampleData> partitioner = new ProducerThreadPartitioner<SampleData>();
        Assert.assertEquals(partitioner.partition(10, new SampleData()), partitionNum);
        Assert.assertEquals(partitioner.partition(10, new SampleData()), partitionNum);
        Assert.assertEquals(partitioner.partition(10, new SampleData()), partitionNum);
    }
}
