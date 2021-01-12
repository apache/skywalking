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

package org.apache.skywalking.apm.commons.datacarrier.consumer;

import org.apache.skywalking.apm.commons.datacarrier.SampleData;
import org.apache.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.apm.commons.datacarrier.buffer.Channels;
import org.apache.skywalking.apm.commons.datacarrier.partition.SimpleRollingPartitioner;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.api.support.membermodification.MemberModifier;

public class ConsumeDriverTest {
    @Test
    public void testBeginConsumeDriver() throws IllegalAccessException {
        Channels<SampleData> channels = new Channels<SampleData>(2, 100, new SimpleRollingPartitioner<SampleData>(), BufferStrategy.BLOCKING);
        ConsumeDriver<SampleData> pool = new ConsumeDriver<SampleData>("default", channels, new SampleConsumer(), 2, 20);
        pool.begin(channels);

        ConsumerThread[] threads = (ConsumerThread[]) MemberModifier.field(ConsumeDriver.class, "consumerThreads")
                                                                    .get(pool);
        Assert.assertEquals(2, threads.length);
        Assert.assertTrue(threads[0].isAlive());
        Assert.assertTrue(threads[1].isAlive());
    }

    @Test
    public void testCloseConsumeDriver() throws InterruptedException, IllegalAccessException {
        Channels<SampleData> channels = new Channels<SampleData>(2, 100, new SimpleRollingPartitioner<SampleData>(), BufferStrategy.BLOCKING);
        ConsumeDriver<SampleData> pool = new ConsumeDriver<SampleData>("default", channels, new SampleConsumer(), 2, 20);
        pool.begin(channels);

        Thread.sleep(5000);
        pool.close(channels);
        ConsumerThread[] threads = (ConsumerThread[]) MemberModifier.field(ConsumeDriver.class, "consumerThreads")
                                                                    .get(pool);

        Assert.assertEquals(2, threads.length);
        Assert.assertFalse((Boolean) MemberModifier.field(ConsumerThread.class, "running").get(threads[0]));
        Assert.assertFalse((Boolean) MemberModifier.field(ConsumerThread.class, "running").get(threads[1]));
    }
}
