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

import java.util.*;
import org.apache.skywalking.apm.commons.datacarrier.buffer.*;
import org.apache.skywalking.apm.commons.datacarrier.partition.SimpleRollingPartitioner;
import org.junit.*;

/**
 * @author wusheng
 */
public class BulkConsumePoolTest {
    @Test
    public void testOneThreadPool() throws InterruptedException {
        BulkConsumePool pool = new BulkConsumePool("testPool", 1, 50);
        final ArrayList<Object> result1 = new ArrayList();
        Channels c1 = new Channels(2, 10, new SimpleRollingPartitioner(), BufferStrategy.OVERRIDE);
        pool.add("test", c1,
            new IConsumer() {
                @Override public void init() {

                }

                @Override public void consume(List data) {
                    for (Object datum : data) {
                        result1.add(datum);
                    }
                }

                @Override public void onError(List data, Throwable t) {

                }

                @Override public void onExit() {

                }
            });
        pool.begin(c1);
        final ArrayList<Object> result2 = new ArrayList();
        Channels c2 = new Channels(2, 10, new SimpleRollingPartitioner(), BufferStrategy.OVERRIDE);
        pool.add("test2", c2,
            new IConsumer() {
                @Override public void init() {

                }

                @Override public void consume(List data) {
                    for (Object datum : data) {
                        result2.add(datum);
                    }
                }

                @Override public void onError(List data, Throwable t) {

                }

                @Override public void onExit() {

                }
            });
        pool.begin(c2);
        c1.save(new Object());
        c1.save(new Object());
        c1.save(new Object());
        c1.save(new Object());
        c1.save(new Object());
        c2.save(new Object());
        c2.save(new Object());
        Thread.sleep(2000);

        Assert.assertEquals(5, result1.size());
        Assert.assertEquals(2, result2.size());
    }
}
