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

package org.apache.skywalking.oap.server.library.datacarrier;

import org.apache.skywalking.oap.server.library.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.oap.server.library.datacarrier.buffer.Channels;
import org.apache.skywalking.oap.server.library.datacarrier.buffer.QueueBuffer;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.library.datacarrier.partition.ProducerThreadPartitioner;
import org.apache.skywalking.oap.server.library.datacarrier.partition.SimpleRollingPartitioner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.powermock.reflect.Whitebox;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataCarrierTest {
    @Test
    public void testCreateDataCarrier() {
        DataCarrier<SampleData> carrier = new DataCarrier<>(5, 100, BufferStrategy.IF_POSSIBLE);

        Channels<SampleData> channels = Whitebox.getInternalState(carrier, "channels");
        assertEquals(5, channels.getChannelSize());

        QueueBuffer<SampleData> buffer = channels.getBuffer(0);
        assertEquals(100, buffer.getBufferSize());

        assertEquals(Whitebox.getInternalState(buffer, "strategy"), BufferStrategy.IF_POSSIBLE);
        assertEquals(Whitebox.getInternalState(buffer, "strategy"), BufferStrategy.IF_POSSIBLE);

        assertEquals(Whitebox.getInternalState(channels, "dataPartitioner").getClass(), SimpleRollingPartitioner.class);
        carrier.setPartitioner(new ProducerThreadPartitioner<>());
        assertEquals(Whitebox.getInternalState(channels, "dataPartitioner").getClass(), ProducerThreadPartitioner.class);
    }

    @Test
    public void testProduce() throws IllegalAccessException {
        DataCarrier<SampleData> carrier = new DataCarrier<>(2, 100);
        assertTrue(carrier.produce(new SampleData().setName("a")));
        assertTrue(carrier.produce(new SampleData().setName("b")));
        assertTrue(carrier.produce(new SampleData().setName("c")));
        assertTrue(carrier.produce(new SampleData().setName("d")));

        Channels<SampleData> channels = Whitebox.getInternalState(carrier, "channels");
        QueueBuffer<SampleData> buffer1 = channels.getBuffer(0);

        List result = new ArrayList();
        buffer1.obtain(result);
        assertEquals(2, result.size());

        QueueBuffer<SampleData> buffer2 = channels.getBuffer(1);
        buffer2.obtain(result);

        assertEquals(4, result.size());

    }

    @Test
    public void testIfPossibleProduce() {
        DataCarrier<SampleData> carrier = new DataCarrier<>(2, 100, BufferStrategy.IF_POSSIBLE);

        for (int i = 0; i < 200; i++) {
            assertTrue(carrier.produce(new SampleData().setName("d" + i)));
        }

        for (int i = 0; i < 200; i++) {
            Assertions.assertFalse(carrier.produce(new SampleData().setName("d" + i + "_2")));
        }

        Channels<SampleData> channels = Whitebox.getInternalState(carrier, "channels");
        QueueBuffer<SampleData> buffer1 = channels.getBuffer(0);
        List<SampleData> result = new ArrayList<>();
        buffer1.obtain(result);

        QueueBuffer<SampleData> buffer2 = channels.getBuffer(1);
        buffer2.obtain(result);
        assertEquals(200, result.size());
    }

    @Test
    public void testBlockingProduce() {
        final DataCarrier<SampleData> carrier = new DataCarrier<>(2, 100);

        for (int i = 0; i < 200; i++) {
            assertTrue(carrier.produce(new SampleData().setName("d" + i)));
        }

        long time1 = System.currentTimeMillis();
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            IConsumer<SampleData> consumer = new IConsumer<SampleData>() {
                @Override
                public void consume(List<SampleData> data) {

                }

                @Override
                public void onError(List<SampleData> data, Throwable t) {

                }
            };
            carrier.consume(consumer, 1);
        }).start();

        carrier.produce(new SampleData().setName("blocking-data"));
        long time2 = System.currentTimeMillis();

        assertTrue(time2 - time1 > 2000);
    }
}
