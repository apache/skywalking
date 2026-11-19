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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.library.elasticsearch.bulk.BulkProcessor;

/**
 * Closes the bulk processor of a {@link BatchProcessEsDAO} for the ITs, which OAP never does: its periodical flush runs
 * as long as OAP. An IT closes it before it shuts the client down, so no flush thread outlives the test. The client
 * module's {@code BulkProcessorITExt} does the same for a processor of its own; test classes are not shared between
 * modules.
 */
public final class BatchProcessEsDAOITExt {
    private BatchProcessEsDAOITExt() {
    }

    /**
     * Stops the periodical flush, lets a flush already running finish its bulk, and sends what is still queued.
     */
    public static void close(final BatchProcessEsDAO dao) {
        final BulkProcessor processor = (BulkProcessor) field(BatchProcessEsDAO.class, "bulkProcessor", dao);
        if (processor == null) {
            return;
        }
        final ScheduledThreadPoolExecutor scheduler =
            (ScheduledThreadPoolExecutor) field(BulkProcessor.class, "scheduler", processor);
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

    private static Object field(final Class<?> type, final String name, final Object target) {
        try {
            final Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getSimpleName() + " has no field named " + name, e);
        }
    }
}
