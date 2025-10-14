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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk;

import io.grpc.stub.AbstractAsyncStub;
import io.grpc.stub.StreamObserver;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.client.AbstractWrite;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;

@Slf4j
public abstract class AbstractBulkWriteProcessor<REQ extends com.google.protobuf.GeneratedMessageV3,
    STUB extends AbstractAsyncStub<STUB>>
    implements Runnable, Closeable {
    private final STUB stub;
    private final int maxBulkSize;
    private final int flushInterval;
    private final ArrayBlockingQueue<Holder> requests;
    private final Semaphore semaphore;
    private final long flushInternalInMillis;
    private final ScheduledThreadPoolExecutor scheduler;
    private final int timeout;
    private volatile long lastFlushTS = 0;

    /**
     * Create the processor.
     *
     * @param stub          an implementation of {@link AbstractAsyncStub}
     * @param processorName name of the processor for logging
     * @param maxBulkSize   the max bulk size for the flush operation
     * @param flushInterval if given maxBulkSize is not reached in this period, the flush would be trigger
     *                      automatically. Unit is second.
     * @param concurrency   the number of concurrency would run for the flush max.
     * @param timeout       network timeout threshold in seconds.
     */
    protected AbstractBulkWriteProcessor(STUB stub,
                                         String processorName,
                                         int maxBulkSize,
                                         int flushInterval,
                                         int concurrency,
                                         int timeout) {
        this.stub = stub;
        this.maxBulkSize = maxBulkSize;
        this.flushInterval = flushInterval;
        this.timeout = timeout;
        requests = new ArrayBlockingQueue<>(maxBulkSize + 1);
        this.semaphore = new Semaphore(concurrency > 0 ? concurrency : 1);

        scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            final Thread thread = new Thread(r);
            thread.setName("BanyanDB BulkProcessor");
            return thread;
        });
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setRemoveOnCancelPolicy(true);
        flushInternalInMillis = flushInterval * 1000;
        scheduler.scheduleWithFixedDelay(
            this, 0, flushInterval, TimeUnit.SECONDS);
    }

    /**
     * Add the measure to the bulk processor.
     *
     * @param writeEntity to add.
     */
    @SneakyThrows
    public CompletableFuture<Void> add(AbstractWrite<REQ> writeEntity) {
        final CompletableFuture<Void> f = new CompletableFuture<>();
        requests.put(Holder.create(writeEntity, f));
        flushIfNeeded();
        return f;
    }

    public void run() {
        try {
            doPeriodicalFlush();
        } catch (Throwable t) {
            log.error("Failed to flush data to BanyanDB", t);
        }
    }

    @SneakyThrows
    protected void flushIfNeeded() {
        if (requests.size() >= maxBulkSize) {
            flush();
        }
    }

    private void doPeriodicalFlush() {
        if (System.currentTimeMillis() - lastFlushTS > flushInternalInMillis / 2) {
            // Run periodical flush if there is no `flushIfNeeded` executed in the second half of the flush period.
            // Otherwise, wait for the next round. By default, the last 2 seconds of the 5s period.
            // This could avoid periodical flush running among bulks(controlled by bulkActions).
            flush();
        }
    }

    public void flush() {
        if (requests.isEmpty()) {
            return;
        }

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            log.error("Interrupted when trying to get semaphore to execute bulk requests", e);
            return;
        }

        final List<Holder> batch = new ArrayList<>(requests.size());
        requests.drainTo(batch);
        final CompletableFuture<Void> future = doObservedFlush(batch);
        future.whenComplete((v, t) -> semaphore.release());
        future.join();
        lastFlushTS = System.currentTimeMillis();

    }

    protected abstract CompletableFuture<Void> doObservedFlush(final List<Holder> data);

    protected CompletableFuture<Void> doFlush(final List<Holder> data, HistogramMetrics.Timer timer) {
        // The batch is used to control the completion of the flush operation.
        // There is at most one error per batch,
        // because the database server would terminate the batch process when the first error occurs.
        final CompletableFuture<Void> batch = new CompletableFuture<>();
        final StreamObserver<REQ> writeRequestStreamObserver
            = this.buildStreamObserver(stub.withDeadlineAfter(timeout, TimeUnit.SECONDS), batch);

        try {
            data.forEach(h -> {
                AbstractWrite<REQ> entity = (AbstractWrite<REQ>) h.getWriteEntity();
                REQ request;
                try {
                    request = entity.build();
                } catch (Throwable bt) {
                    log.error("building the entity fails: {}", entity.toString(), bt);
                    h.getFuture().completeExceptionally(bt);
                    return;
                }
                writeRequestStreamObserver.onNext(request);
                h.getFuture().complete(null);
            });
        } finally {
            writeRequestStreamObserver.onCompleted();
        }
        batch.whenComplete((ignored, exp) -> {
            timer.close();
            if (exp != null) {
                log.error("Failed to execute requests in bulk", exp);
            }
        });
        return batch;
    }

    public void close() {
        scheduler.shutdownNow();
    }

    protected abstract StreamObserver<REQ> buildStreamObserver(STUB stub, CompletableFuture<Void> batch);

    @Getter
    static class Holder {
        private final AbstractWrite<?> writeEntity;
        private final CompletableFuture<Void> future;

        private Holder(AbstractWrite<?> writeEntity, CompletableFuture<Void> future) {
            this.writeEntity = writeEntity;
            this.future = future;
        }

        public static <REQ extends com.google.protobuf.GeneratedMessageV3> Holder create(AbstractWrite<REQ> writeEntity,
                                                                                         CompletableFuture<Void> future) {
            future.whenComplete((v, t) -> {
                if (t != null) {
                    log.error("Failed to execute the request: {}", writeEntity.toString(), t);
                }
            });
            return new Holder(writeEntity, future);
        }
    }
}
