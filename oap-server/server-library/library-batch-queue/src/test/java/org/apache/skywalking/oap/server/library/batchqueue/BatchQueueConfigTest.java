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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BatchQueueConfigTest {

    @Test
    public void testDedicatedThreadConfig() {
        final BatchQueueConfig<String> config = BatchQueueConfig.<String>builder()
            .threads(ThreadPolicy.fixed(2))
            .build();
        config.validate();
        assertNotNull(config.getThreads());
    }

    @Test
    public void testSharedSchedulerConfig() {
        final BatchQueueConfig<String> config = BatchQueueConfig.<String>builder()
            .sharedScheduler("IO_POOL", ThreadPolicy.fixed(4))
            .build();
        config.validate();
        assertEquals("IO_POOL", config.getSharedSchedulerName());
        assertNotNull(config.getSharedSchedulerThreads());
    }

    @Test
    public void testRejectsBothThreadsAndShared() {
        final BatchQueueConfig<String> config = BatchQueueConfig.<String>builder()
            .threads(ThreadPolicy.fixed(2))
            .sharedScheduler("IO_POOL", ThreadPolicy.fixed(4))
            .build();
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    public void testRejectsNeitherThreadsNorShared() {
        final BatchQueueConfig<String> config = BatchQueueConfig.<String>builder()
            .build();
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    public void testRejectsSharedWithoutThreadPolicy() {
        final BatchQueueConfig<String> config = BatchQueueConfig.<String>builder()
            .sharedSchedulerName("IO_POOL")
            .build();
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    public void testRejectsZeroBufferSize() {
        final BatchQueueConfig<String> config = BatchQueueConfig.<String>builder()
            .threads(ThreadPolicy.fixed(1))
            .bufferSize(0)
            .build();
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    public void testRejectsZeroMinIdleMs() {
        final BatchQueueConfig<String> config = BatchQueueConfig.<String>builder()
            .threads(ThreadPolicy.fixed(1))
            .minIdleMs(0)
            .build();
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    public void testRejectsMaxIdleLessThanMinIdle() {
        final BatchQueueConfig<String> config = BatchQueueConfig.<String>builder()
            .threads(ThreadPolicy.fixed(1))
            .minIdleMs(100)
            .maxIdleMs(50)
            .build();
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    public void testDefaults() {
        final BatchQueueConfig<String> config = BatchQueueConfig.<String>builder()
            .threads(ThreadPolicy.fixed(1))
            .build();
        assertEquals(10_000, config.getBufferSize());
        assertEquals(BufferStrategy.BLOCKING, config.getStrategy());
        assertEquals(1, config.getMinIdleMs());
        assertEquals(50, config.getMaxIdleMs());
    }
}
