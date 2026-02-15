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

package org.apache.skywalking.oap.server.library.batchqueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchQueueManagerTest {

    @AfterEach
    public void cleanup() {
        BatchQueueManager.reset();
    }

    @Test
    public void testCreateAndGet() {
        final BatchQueue<String> queue = BatchQueueManager.create("test-queue",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        assertNotNull(queue);
        assertEquals("test-queue", queue.getName());

        final BatchQueue<String> retrieved = BatchQueueManager.get("test-queue");
        assertSame(queue, retrieved);
    }

    @Test
    public void testCreateThrowsOnDuplicateName() {
        final BatchQueueConfig<String> config = BatchQueueConfig.<String>builder()
            .threads(ThreadPolicy.fixed(1))
            .consumer(data -> { })
            .bufferSize(100)
            .build();

        BatchQueueManager.create("absent-test", config);
        assertThrows(IllegalStateException.class,
            () -> BatchQueueManager.create("absent-test", config));
    }

    @Test
    public void testGetNonExistentReturnsNull() {
        assertNull(BatchQueueManager.get("nonexistent"));
    }

    @Test
    public void testShutdownRemovesQueue() {
        BatchQueueManager.create("shutdown-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        assertNotNull(BatchQueueManager.get("shutdown-test"));
        BatchQueueManager.shutdown("shutdown-test");
        assertNull(BatchQueueManager.get("shutdown-test"));
    }

    @Test
    public void testShutdownAllClearsEverything() {
        BatchQueueManager.create("q1",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        BatchQueueManager.create("q2",
            BatchQueueConfig.<String>builder()
                .sharedScheduler("SHARED", ThreadPolicy.fixed(2))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        BatchQueueManager.shutdownAll();

        assertNull(BatchQueueManager.get("q1"));
        assertNull(BatchQueueManager.get("q2"));
    }

    @Test
    public void testSharedSchedulerCreatedLazily() {
        // First queue referencing shared scheduler creates it
        final BatchQueue<String> q1 = BatchQueueManager.create("lazy1",
            BatchQueueConfig.<String>builder()
                .sharedScheduler("LAZY_POOL", ThreadPolicy.fixed(2))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        // Second queue uses the same pool
        final BatchQueue<String> q2 = BatchQueueManager.create("lazy2",
            BatchQueueConfig.<String>builder()
                .sharedScheduler("LAZY_POOL", ThreadPolicy.fixed(2))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        assertNotNull(q1);
        assertNotNull(q2);
        // Both should be running on shared scheduler (not dedicated)
        assertNotNull(BatchQueueManager.get("lazy1"));
        assertNotNull(BatchQueueManager.get("lazy2"));
    }

    @Test
    public void testSharedSchedulerRefCounting() {
        final BatchQueueConfig<String> config = BatchQueueConfig.<String>builder()
            .sharedScheduler("REF_POOL", ThreadPolicy.fixed(2))
            .consumer(data -> { })
            .bufferSize(100)
            .build();

        BatchQueueManager.create("ref1", config);
        BatchQueueManager.create("ref2", config);

        // Capture the scheduler before any shutdown
        final ScheduledExecutorService scheduler =
            BatchQueueManager.getOrCreateSharedScheduler("REF_POOL", ThreadPolicy.fixed(2));
        // Release the extra ref from getOrCreateSharedScheduler call above
        BatchQueueManager.releaseSharedScheduler("REF_POOL");

        assertFalse(scheduler.isShutdown());

        // Shutting down first queue should NOT shut down the shared scheduler
        BatchQueueManager.shutdown("ref1");
        assertFalse(scheduler.isShutdown());

        // Shutting down last queue SHOULD shut down the shared scheduler
        BatchQueueManager.shutdown("ref2");
        assertTrue(scheduler.isShutdown());
    }
}
