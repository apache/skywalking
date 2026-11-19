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

package org.apache.skywalking.library.elasticsearch.bulk;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

class BulkProcessorITExtTest {
    /**
     * The periodical flush runs on a thread of the processor's own, which an IT has to end, or every processor it
     * leaves behind keeps a thread alive.
     */
    @Test
    void endsTheFlushThreadOnClose() {
        final long running = flushThreads();
        final BulkProcessor processor = BulkProcessor.builder()
                                                     .bulkActions(10)
                                                     .batchOfBytes(1024)
                                                     .flushInterval(Duration.ofSeconds(1))
                                                     .concurrentRequests(1)
                                                     .bulkMetrics(mock(HistogramMetrics.class))
                                                     .build(new AtomicReference<>());
        await().atMost(10, TimeUnit.SECONDS).until(() -> flushThreads() == running + 1);

        BulkProcessorITExt.close(processor);

        await().atMost(10, TimeUnit.SECONDS).until(() -> flushThreads() == running);
    }

    private static long flushThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                     .filter(thread -> thread.isAlive() && "ElasticSearch BulkProcessor".equals(thread.getName()))
                     .count();
    }
}
