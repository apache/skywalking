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

package org.apache.skywalking.library.banyandb.v1.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.measure.v1.BanyandbMeasure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MeasureWriteMessageIdTest {

    private static final BanyandbCommon.Metadata METADATA =
        BanyandbCommon.Metadata.newBuilder()
                               .setGroup("test-group")
                               .setName("test-measure")
                               .build();

    @Test
    public void messageIdMatchesDataPointVersion() {
        final MeasureWrite write = new MeasureWrite(METADATA, System.currentTimeMillis());
        final BanyandbMeasure.WriteRequest request = write.build();
        assertEquals(request.getMessageId(), request.getDataPoint().getVersion());
        assertTrue(request.getMessageId() > 0);
    }

    @Test
    public void messageIdMatchesDataPointVersionInBuildValues() {
        final MeasureWrite write = new MeasureWrite(METADATA, System.currentTimeMillis());
        final BanyandbMeasure.WriteRequest request = write.buildOnlyValues();
        assertEquals(request.getMessageId(), request.getDataPoint().getVersion());
        assertTrue(request.getMessageId() > 0);
    }

    @Test
    public void messageIdsAreStrictlyIncreasing() {
        final List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(AbstractWrite.nextMessageId());
        }
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i) > ids.get(i - 1), "message ids must be strictly increasing");
        }
    }

    @Test
    public void messageIdsAreUniqueUnderConcurrency() throws InterruptedException {
        final int threads = 8;
        final int perThread = 2_000;
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final Set<Long> seen = ConcurrentHashMap.newKeySet();
        final CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            seen.add(AbstractWrite.nextMessageId());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent writes timed out");
        } finally {
            executor.shutdownNow();
        }
        assertEquals(threads * perThread, seen.size(), "all message ids must be unique");
    }
}