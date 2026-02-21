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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    public void testGetOrCreateReturnsSameInstance() {
        final BatchQueueConfig<String> config = BatchQueueConfig.<String>builder()
            .threads(ThreadPolicy.fixed(1))
            .consumer(data -> { })
            .bufferSize(100)
            .build();

        final BatchQueue<String> first = BatchQueueManager.getOrCreate("shared-test", config);
        final BatchQueue<String> second = BatchQueueManager.getOrCreate("shared-test", config);
        assertSame(first, second);
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
                .threads(ThreadPolicy.fixed(1))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        BatchQueueManager.shutdownAll();

        assertNull(BatchQueueManager.get("q1"));
        assertNull(BatchQueueManager.get("q2"));
    }
}
