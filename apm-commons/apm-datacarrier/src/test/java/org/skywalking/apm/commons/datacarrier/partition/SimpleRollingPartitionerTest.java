package org.skywalking.apm.commons.datacarrier.partition;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.commons.datacarrier.SampleData;

/**
 * Created by wusheng on 2016/10/25.
 */
public class SimpleRollingPartitionerTest {
    @Test
    public void testPartition() {
        SimpleRollingPartitioner<SampleData> partitioner = new SimpleRollingPartitioner<SampleData>();
        Assert.assertEquals(partitioner.partition(10, new SampleData()), 0);
        Assert.assertEquals(partitioner.partition(10, new SampleData()), 1);
        Assert.assertEquals(partitioner.partition(10, new SampleData()), 2);
    }
}
