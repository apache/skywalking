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

import org.apache.skywalking.oap.server.library.datacarrier.DataCarrier;
import org.apache.skywalking.oap.server.library.datacarrier.SampleData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.powermock.reflect.Whitebox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class ConsumerTest {
    public static LinkedBlockingQueue<SampleData> BUFFER = new LinkedBlockingQueue<>();

    public static boolean IS_OCCUR_ERROR = false;

    @Test
    public void testConsumerLessThanChannel() throws IllegalAccessException {

        final DataCarrier<SampleData> carrier = new DataCarrier<>(2, 100);

        for (int i = 0; i < 100; i++) {
            Assertions.assertTrue(carrier.produce(new SampleData().setName("data" + i)));
        }
        SampleConsumer consumer = new SampleConsumer();

        consumer.i = 100;
        carrier.consume(SampleConsumer.class, 1);
        Assertions.assertEquals(1, ((SampleConsumer) getConsumer(carrier)).i);

        SampleConsumer2 consumer2 = new SampleConsumer2();
        consumer2.i = 100;
        carrier.consume(consumer2, 1);
        Assertions.assertEquals(100, ((SampleConsumer2) getConsumer(carrier)).i);

        carrier.shutdownConsumers();
    }

    @Test
    public void testConsumerMoreThanChannel() throws InterruptedException {
        BUFFER.drainTo(new ArrayList<SampleData>());
        final DataCarrier<SampleData> carrier = new DataCarrier<SampleData>(2, 100);

        for (int i = 0; i < 200; i++) {
            Assertions.assertTrue(carrier.produce(new SampleData().setName("data" + i)));
        }
        SampleConsumer consumer = new SampleConsumer();

        carrier.consume(SampleConsumer.class, 5);

        Thread.sleep(2000);

        List<SampleData> result = new ArrayList<SampleData>();
        BUFFER.drainTo(result);

        Assertions.assertEquals(200, result.size());

        HashSet<Integer> consumerCounter = new HashSet<Integer>();
        for (SampleData data : result) {
            consumerCounter.add(data.getIntValue());
        }
        Assertions.assertEquals(2, consumerCounter.size());
    }

    @Test
    public void testConsumerOnError() throws InterruptedException {
        final DataCarrier<SampleData> carrier = new DataCarrier<SampleData>(2, 100);

        for (int i = 0; i < 200; i++) {
            Assertions.assertTrue(carrier.produce(new SampleData().setName("data" + i)));
        }
        SampleConsumer2 consumer = new SampleConsumer2();

        consumer.onError = true;
        carrier.consume(consumer, 5);

        Thread.sleep(3 * 1000L);

        Assertions.assertTrue(IS_OCCUR_ERROR);
    }

    class SampleConsumer2 implements IConsumer<SampleData> {
        public int i = 1;

        public boolean onError = false;

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
    }

    private IConsumer getConsumer(DataCarrier<SampleData> carrier) throws IllegalAccessException {
        ConsumeDriver pool = Whitebox.getInternalState(carrier, "driver");
        ConsumerThread[] threads = Whitebox.getInternalState(pool, "consumerThreads");

        return Whitebox.getInternalState(threads[0], "consumer");
    }
}
