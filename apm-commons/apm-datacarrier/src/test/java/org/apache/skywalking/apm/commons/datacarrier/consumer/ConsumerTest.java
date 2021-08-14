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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.SampleData;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.api.support.membermodification.MemberModifier;

public class ConsumerTest {
    public static LinkedBlockingQueue<SampleData> BUFFER = new LinkedBlockingQueue<SampleData>();

    public static boolean IS_OCCUR_ERROR = false;

    @Test
    public void testConsumerLessThanChannel() throws IllegalAccessException {

        final DataCarrier<SampleData> carrier = new DataCarrier<SampleData>(2, 100);

        for (int i = 0; i < 100; i++) {
            Assert.assertTrue(carrier.produce(new SampleData().setName("data" + i)));
        }
        SampleConsumer consumer = new SampleConsumer();

        consumer.i = 100;
        carrier.consume(SampleConsumer.class, 1);
        Assert.assertEquals(1, ((SampleConsumer) getConsumer(carrier)).i);

        SampleConsumer2 consumer2 = new SampleConsumer2();
        consumer2.i = 100;
        carrier.consume(consumer2, 1);
        Assert.assertEquals(100, ((SampleConsumer2) getConsumer(carrier)).i);

        carrier.shutdownConsumers();
    }

    @Test
    public void testConsumerMoreThanChannel() throws IllegalAccessException, InterruptedException {
        BUFFER.drainTo(new ArrayList<SampleData>());
        final DataCarrier<SampleData> carrier = new DataCarrier<SampleData>(2, 100);

        for (int i = 0; i < 200; i++) {
            Assert.assertTrue(carrier.produce(new SampleData().setName("data" + i)));
        }
        SampleConsumer consumer = new SampleConsumer();

        carrier.consume(SampleConsumer.class, 5);

        Thread.sleep(2000);

        List<SampleData> result = new ArrayList<SampleData>();
        BUFFER.drainTo(result);

        Assert.assertEquals(200, result.size());

        HashSet<Integer> consumerCounter = new HashSet<Integer>();
        for (SampleData data : result) {
            consumerCounter.add(data.getIntValue());
        }
        Assert.assertEquals(2, consumerCounter.size());
    }

    @Test
    public void testConsumerOnError() throws InterruptedException {
        final DataCarrier<SampleData> carrier = new DataCarrier<SampleData>(2, 100);

        for (int i = 0; i < 200; i++) {
            Assert.assertTrue(carrier.produce(new SampleData().setName("data" + i)));
        }
        SampleConsumer2 consumer = new SampleConsumer2();

        consumer.onError = true;
        carrier.consume(consumer, 5);

        Thread.sleep(3 * 1000L);

        Assert.assertTrue(IS_OCCUR_ERROR);
    }

    class SampleConsumer2 implements IConsumer<SampleData> {
        public int i = 1;

        public boolean onError = false;

        @Override
        public void init(final Properties properties) {

        }

        @Override
        public void consume(List<SampleData> data) {
            if (onError) {
                throw new RuntimeException("consume exception");
            }
        }

        @Override
        public void onError(List<SampleData> data, Throwable t) {
            IS_OCCUR_ERROR = true;
        }

        @Override
        public void onExit() {

        }
    }

    private IConsumer getConsumer(DataCarrier<SampleData> carrier) throws IllegalAccessException {
        ConsumeDriver pool = (ConsumeDriver) MemberModifier.field(DataCarrier.class, "driver").get(carrier);
        ConsumerThread[] threads = (ConsumerThread[]) MemberModifier.field(ConsumeDriver.class, "consumerThreads")
                                                                    .get(pool);

        return (IConsumer) MemberModifier.field(ConsumerThread.class, "consumer").get(threads[0]);
    }
}
