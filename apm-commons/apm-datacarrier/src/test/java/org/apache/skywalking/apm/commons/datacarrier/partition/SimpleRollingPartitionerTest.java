/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.commons.datacarrier.partition;

import org.apache.skywalking.apm.commons.datacarrier.SampleData;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleRollingPartitionerTest {
    @Test
    public void testPartition() {
        SimpleRollingPartitioner<SampleData> partitioner = new SimpleRollingPartitioner<SampleData>();
        Assert.assertEquals(partitioner.partition(10, new SampleData()), 0);
        Assert.assertEquals(partitioner.partition(10, new SampleData()), 1);
        Assert.assertEquals(partitioner.partition(10, new SampleData()), 2);
    }

    @Test
    public void testPartitionWithMultiThreads() throws InterruptedException {
        final SimpleRollingPartitioner<SampleData> partitioner = new SimpleRollingPartitioner<>();
        final SampleData sampleData = new SampleData();
        final int nPartition = 5;
        final int nTimesPerPartition = 2000;
        final int nThread = 10;
        final ConcurrentMap<Integer, AtomicInteger> result = new ConcurrentHashMap<>();
        for (int i = 0; i < nPartition; i++) {
            result.put(i, new AtomicInteger());
        }

        final CountDownLatch countDownLatch = new CountDownLatch(nThread);
        for (int i = 0; i < nThread; i++) {
            new Thread(() -> {
                for (int j = 0; j < nTimesPerPartition; j++) {
                    int partition = partitioner.partition(nPartition, sampleData);
                    result.get(partition).incrementAndGet();
                }
                countDownLatch.countDown();
            }).start();
        }
        countDownLatch.await();

        final int exceptedCountPerPartition = nThread * nTimesPerPartition / nPartition;
        for (Map.Entry<Integer, AtomicInteger> entry : result.entrySet()) {
            Assert.assertEquals(exceptedCountPerPartition, entry.getValue().get());
        }
    }
}
