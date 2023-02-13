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

package org.apache.skywalking.oap.server.library.datacarrier.consumer;

import org.apache.skywalking.oap.server.library.datacarrier.SampleData;
import org.apache.skywalking.oap.server.library.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.oap.server.library.datacarrier.buffer.Channels;
import org.apache.skywalking.oap.server.library.datacarrier.partition.SimpleRollingPartitioner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.powermock.reflect.Whitebox;

public class ConsumeDriverTest {
    @Test
    public void testBeginConsumeDriver() {
        Channels<SampleData> channels = new Channels<SampleData>(2, 100, new SimpleRollingPartitioner<SampleData>(), BufferStrategy.BLOCKING);
        ConsumeDriver<SampleData> pool = new ConsumeDriver<SampleData>("default", channels, new SampleConsumer(), 2, 20);
        pool.begin(channels);

        ConsumerThread[] threads = Whitebox.getInternalState(pool, "consumerThreads");
        Assertions.assertEquals(2, threads.length);
        Assertions.assertTrue(threads[0].isAlive());
        Assertions.assertTrue(threads[1].isAlive());
    }

    @Test
    public void testCloseConsumeDriver() throws InterruptedException, IllegalAccessException {
        Channels<SampleData> channels = new Channels<SampleData>(2, 100, new SimpleRollingPartitioner<SampleData>(), BufferStrategy.BLOCKING);
        ConsumeDriver<SampleData> pool = new ConsumeDriver<SampleData>("default", channels, new SampleConsumer(), 2, 20);
        pool.begin(channels);

        Thread.sleep(5000);
        pool.close(channels);
        ConsumerThread[] threads = Whitebox.getInternalState(pool, "consumerThreads");

        Assertions.assertEquals(2, threads.length);
        Assertions.assertFalse((Boolean) Whitebox.getInternalState(threads[0], "running"));
        Assertions.assertFalse((Boolean) Whitebox.getInternalState(threads[1], "running"));
    }
}
