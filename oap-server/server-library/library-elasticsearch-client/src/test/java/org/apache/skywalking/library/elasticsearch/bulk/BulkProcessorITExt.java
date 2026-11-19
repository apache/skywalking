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

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Closes a {@link BulkProcessor} for the ITs, which OAP never does: its periodical flush runs as long as OAP. An IT
 * closes its processors before it shuts the client down, so no flush thread outlives the test.
 */
public final class BulkProcessorITExt {
    private BulkProcessorITExt() {
    }

    /**
     * Stops the periodical flush, lets a flush already running finish its bulk, and sends what is still queued.
     */
    public static void close(final BulkProcessor processor) {
        final ScheduledThreadPoolExecutor scheduler = scheduler(processor);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.MINUTES)) {
                throw new IllegalStateException("The periodical flush did not finish in a minute");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while the periodical flush finished", e);
        }
        processor.flush();
    }

    private static ScheduledThreadPoolExecutor scheduler(final BulkProcessor processor) {
        try {
            final Field field = BulkProcessor.class.getDeclaredField("scheduler");
            field.setAccessible(true);
            return (ScheduledThreadPoolExecutor) field.get(processor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("BulkProcessor keeps its periodical flush in a field named scheduler", e);
        }
    }
}
