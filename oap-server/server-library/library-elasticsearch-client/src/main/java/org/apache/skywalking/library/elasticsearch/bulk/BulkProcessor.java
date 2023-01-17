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
 */

package org.apache.skywalking.library.elasticsearch.bulk;

import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.util.Exceptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.ElasticSearch;
import org.apache.skywalking.library.elasticsearch.requests.IndexRequest;
import org.apache.skywalking.library.elasticsearch.requests.UpdateRequest;
import org.apache.skywalking.library.elasticsearch.requests.factory.RequestFactory;
import org.apache.skywalking.oap.server.library.util.RunnableWithExceptionProtection;

import static java.util.Objects.requireNonNull;

@Slf4j
public final class BulkProcessor {
    private final ArrayBlockingQueue<Holder> requests;

    private final AtomicReference<ElasticSearch> es;
    private final int bulkActions;
    private final Semaphore semaphore;
    private final long flushInternalInMillis;
    private volatile long lastFlushTS = 0;
    private final int batchOfBytes;
    private volatile int bufferOfBytes = 0;

    public static BulkProcessorBuilder builder() {
        return new BulkProcessorBuilder();
    }

    BulkProcessor(
        final AtomicReference<ElasticSearch> es, final int bulkActions,
        final Duration flushInterval, final int concurrentRequests, final int batchOfBytes) {
        requireNonNull(flushInterval, "flushInterval");

        this.es = requireNonNull(es, "es");
        this.bulkActions = bulkActions;
        this.batchOfBytes = batchOfBytes;
        this.semaphore = new Semaphore(concurrentRequests > 0 ? concurrentRequests : 1);
        this.requests = new ArrayBlockingQueue<>(bulkActions + 1);

        final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
            1, r -> {
            final Thread thread = new Thread(r);
            thread.setName("ElasticSearch BulkProcessor");
            return thread;
        });
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setRemoveOnCancelPolicy(true);
        flushInternalInMillis = flushInterval.getSeconds() * 1000;
        scheduler.scheduleWithFixedDelay(
            new RunnableWithExceptionProtection(
                this::doPeriodicalFlush,
                t -> log.error("flush data to ES failure:", t)
            ), 0, flushInterval.getSeconds(), TimeUnit.SECONDS);
    }

    public CompletableFuture<Void> add(IndexRequest request) {
        return internalAdd(request);
    }

    public CompletableFuture<Void> add(UpdateRequest request) {
        return internalAdd(request);
    }

    @SneakyThrows
    private CompletableFuture<Void> internalAdd(Object request) {
        requireNonNull(request, "request");
        final CompletableFuture<Void> f = new CompletableFuture<>();
        int len = toByteArray(request).length;
        bufferOfBytes += len;
        requests.put(new Holder(f, request));
        flushIfNeeded();
        return f;
    }

    @SneakyThrows
    private void flushIfNeeded() {
        if (bufferOfBytes > batchOfBytes || requests.size() >= bulkActions) {
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
        bufferOfBytes = 0;
        final CompletableFuture<Void> flush = doFlush(batch);
        flush.whenComplete((ignored1, ignored2) -> semaphore.release());
        flush.join();

        lastFlushTS = System.currentTimeMillis();
    }

    private CompletableFuture<Void> doFlush(final List<Holder> batch) {
        log.debug("Executing bulk with {} requests", batch.size());

        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        final CompletableFuture<Void> future = es.get().version().thenCompose(v -> {
            try {
                final RequestFactory rf = v.requestFactory();
                final List<byte[]> bs = new ArrayList<>();
                for (final Holder holder : batch) {
                    bs.add(v.codec().encode(holder.request));
                    bs.add("\n".getBytes());
                }
                final ByteBuf content = Unpooled.wrappedBuffer(bs.toArray(new byte[0][]));
                return es.get().client().execute(rf.bulk().bulk(content))
                         .aggregate().thenAccept(response -> {
                        final HttpStatus status = response.status();
                        if (status != HttpStatus.OK) {
                            throw new RuntimeException(response.contentUtf8());
                        }
                    });
            } catch (Exception e) {
                return Exceptions.throwUnsafely(e);
            }
        });
        future.whenComplete((ignored, exception) -> {
            if (exception != null) {
                batch.stream().map(it -> it.future)
                     .forEach(it -> it.completeExceptionally(exception));
                log.error("Failed to execute requests in bulk", exception);
            } else {
                log.debug("Succeeded to execute {} requests in bulk", batch.size());
                batch.stream().map(it -> it.future).forEach(it -> it.complete(null));
            }
        });
        return future;
    }

    private byte[] toByteArray(Object obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(
            bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        }
    }

    @RequiredArgsConstructor
    static class Holder {
        private final CompletableFuture<Void> future;
        private final Object request;
    }

}
