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


package org.apache.skywalking.apm.commons.datacarrier.common;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

/**
 * Created by xin on 2017/7/14.
 */
public class AtomicRangeIntegerTest {
    @Test
    public void testGetAndIncrement() {
        AtomicRangeInteger atomicI = new AtomicRangeInteger(0, 10);
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(i, atomicI.getAndIncrement());
        }
        Assert.assertEquals(0, atomicI.getAndIncrement());
        Assert.assertEquals(1, atomicI.get());
        Assert.assertEquals(1, atomicI.intValue());
        Assert.assertEquals(1, atomicI.longValue());
        Assert.assertEquals(1, (int)atomicI.floatValue());
        Assert.assertEquals(1, (int)atomicI.doubleValue());
    }

    @Test
    public void testGetAndIncrementPerformance() {

        int[] threadNums = {4, 8, 16, 32, 64, 128, 256, 512, 1024};

        System.out.println("======== AtomicRangeInteger.getAndIncrement() Performance test start ========");
        for (int i = 0; i< threadNums.length; i++) {
            System.out.println(threadNums[i] + "_threads"
                    + "    new:" + getGetAndIncrementAvgCost(threadNums[i], false)
                    + "    ori:" + getGetAndIncrementAvgCost(threadNums[i], true));
        }
        System.out.println("======== AtomicRangeInteger.getAndIncrement() Performance test end  ========");
    }

    private long getGetAndIncrementAvgCost(int threadNum, final boolean isOriFun) {
        final int loop = 100000;
        final AtomicRangeInteger atomicI = new AtomicRangeInteger(0, 100);
        Thread[] threads = new Thread[threadNum];
        final CountDownLatch countDownLatch = new CountDownLatch(threadNum);
        final long[] tsArr = new long[threadNum];

        for (int i = 0; i < threadNum; i++) {
            final int currentThread = i;
            threads[i] = new Thread(new Runnable() {
                @Override public void run() {
                    long ts = System.currentTimeMillis();
                    for (int j = 0; j < loop; j++) {
                        if (isOriFun) {
                            atomicI.oriGetAndIncrement();
                        } else {
                            atomicI.getAndIncrement();
                        }
                    }
                    tsArr[currentThread] = System.currentTimeMillis() - ts;
                    countDownLatch.countDown();
                }
            });
            threads[i].start();
        }
        try {
            countDownLatch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
        long tsTotal = 0;
        for (Long ts : tsArr) {
            tsTotal += ts;
        }
        return tsTotal / threadNum;
    }
}
