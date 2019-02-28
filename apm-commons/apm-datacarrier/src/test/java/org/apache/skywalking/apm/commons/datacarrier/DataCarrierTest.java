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


package org.apache.skywalking.apm.commons.datacarrier;

import java.util.List;
import org.apache.skywalking.apm.commons.datacarrier.buffer.Buffer;
import org.apache.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.apm.commons.datacarrier.buffer.Channels;
import org.apache.skywalking.apm.commons.datacarrier.partition.ProducerThreadPartitioner;
import org.apache.skywalking.apm.commons.datacarrier.partition.SimpleRollingPartitioner;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.api.support.membermodification.MemberModifier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;

/**
 * Created by wusheng on 2016/10/25.
 */
public class DataCarrierTest {
    @Test
    public void testCreateDataCarrier() throws IllegalAccessException {
        DataCarrier<SampleData> carrier = new DataCarrier<SampleData>(5, 100);
        Assert.assertEquals(((Integer)(MemberModifier.field(DataCarrier.class, "bufferSize").get(carrier))).intValue(), 100);
        Assert.assertEquals(((Integer)(MemberModifier.field(DataCarrier.class, "channelSize").get(carrier))).intValue(), 5);

        Channels<SampleData> channels = (Channels<SampleData>)(MemberModifier.field(DataCarrier.class, "channels").get(carrier));
        Assert.assertEquals(channels.getChannelSize(), 5);

        Buffer<SampleData> buffer = channels.getBuffer(0);
        Assert.assertEquals(buffer.getBufferSize(), 100);

        Assert.assertEquals(MemberModifier.field(Buffer.class, "strategy").get(buffer), BufferStrategy.BLOCKING);
        carrier.setBufferStrategy(BufferStrategy.IF_POSSIBLE);
        Assert.assertEquals(MemberModifier.field(Buffer.class, "strategy").get(buffer), BufferStrategy.IF_POSSIBLE);

        Assert.assertEquals(MemberModifier.field(Channels.class, "dataPartitioner").get(channels).getClass(), SimpleRollingPartitioner.class);
        carrier.setPartitioner(new ProducerThreadPartitioner<SampleData>());
        Assert.assertEquals(MemberModifier.field(Channels.class, "dataPartitioner").get(channels).getClass(), ProducerThreadPartitioner.class);
    }

    @Test
    public void testProduce() throws IllegalAccessException {
        DataCarrier<SampleData> carrier = new DataCarrier<SampleData>(2, 100);
        Assert.assertTrue(carrier.produce(new SampleData().setName("a")));
        Assert.assertTrue(carrier.produce(new SampleData().setName("b")));
        Assert.assertTrue(carrier.produce(new SampleData().setName("c")));
        Assert.assertTrue(carrier.produce(new SampleData().setName("d")));

        Channels<SampleData> channels = (Channels<SampleData>)(MemberModifier.field(DataCarrier.class, "channels").get(carrier));
        Buffer<SampleData> buffer1 = channels.getBuffer(0);
        List result1 = buffer1.obtain(0, 100);

        Buffer<SampleData> buffer2 = channels.getBuffer(1);
        List result2 = buffer2.obtain(0, 100);

        Assert.assertEquals(2, result1.size());
        Assert.assertEquals(4, result1.size() + result2.size());

    }

    @Test
    public void testOverrideProduce() throws IllegalAccessException {
        DataCarrier<SampleData> carrier = new DataCarrier<SampleData>(2, 100);
        carrier.setBufferStrategy(BufferStrategy.OVERRIDE);

        for (int i = 0; i < 500; i++) {
            Assert.assertTrue(carrier.produce(new SampleData().setName("d" + i)));
        }

        Channels<SampleData> channels = (Channels<SampleData>)(MemberModifier.field(DataCarrier.class, "channels").get(carrier));
        Buffer<SampleData> buffer1 = channels.getBuffer(0);
        List result1 = buffer1.obtain(0, 100);

        Buffer<SampleData> buffer2 = channels.getBuffer(1);
        List result2 = buffer2.obtain(0, 100);
        Assert.assertEquals(200, result1.size() + result2.size());
    }

    @Test
    public void testIfPossibleProduce() throws IllegalAccessException {
        DataCarrier<SampleData> carrier = new DataCarrier<SampleData>(2, 100);
        carrier.setBufferStrategy(BufferStrategy.IF_POSSIBLE);

        for (int i = 0; i < 200; i++) {
            Assert.assertTrue(carrier.produce(new SampleData().setName("d" + i)));
        }

        for (int i = 0; i < 200; i++) {
            Assert.assertFalse(carrier.produce(new SampleData().setName("d" + i + "_2")));
        }

        Channels<SampleData> channels = (Channels<SampleData>)(MemberModifier.field(DataCarrier.class, "channels").get(carrier));
        Buffer<SampleData> buffer1 = channels.getBuffer(0);
        List result1 = buffer1.obtain(0, 100);

        Buffer<SampleData> buffer2 = channels.getBuffer(1);
        List result2 = buffer2.obtain(0, 100);
        Assert.assertEquals(200, result1.size() + result2.size());
    }

    @Test
    public void testBlockingProduce() throws IllegalAccessException {
        final DataCarrier<SampleData> carrier = new DataCarrier<SampleData>(2, 100);

        for (int i = 0; i < 200; i++) {
            Assert.assertTrue(carrier.produce(new SampleData().setName("d" + i)));
        }

        long time1 = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                IConsumer<SampleData> consumer = new IConsumer<SampleData>() {
                    int i = 0;

                    @Override
                    public void init() {

                    }

                    @Override
                    public void consume(List<SampleData> data) {

                    }

                    @Override
                    public void onError(List<SampleData> data, Throwable t) {

                    }

                    @Override
                    public void onExit() {

                    }
                };
                carrier.consume(consumer, 1);
            }
        }).start();

        carrier.produce(new SampleData().setName("blocking-data"));
        long time2 = System.currentTimeMillis();

        Assert.assertTrue(time2 - time1 > 2000);
    }
}
