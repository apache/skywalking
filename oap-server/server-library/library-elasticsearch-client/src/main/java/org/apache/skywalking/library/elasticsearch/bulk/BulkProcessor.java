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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
import org.apache.skywalking.library.elasticsearch.requests.factory.Codec;
import org.apache.skywalking.library.elasticsearch.requests.factory.RequestFactory;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
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

    public static BulkProcessorBuilder builder() {
        return new BulkProcessorBuilder();
    }

    BulkProcessor(final AtomicReference<ElasticSearch> es,
                  final int bulkActions,
                  final Duration flushInterval,
                  final int concurrentRequests,
                  final int batchOfBytes) {
        requireNonNull(flushInterval, "flushInterval");

        this.es = requireNonNull(es, "es");
        this.bulkActions = bulkActions;
        this.batchOfBytes = batchOfBytes;
        this.semaphore = new Semaphore(concurrentRequests > 0 ? concurrentRequests : 1);
        this.requests = new ArrayBlockingQueue<>(bulkActions + 1);

        final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, r -> {
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
        requests.put(new Holder(f, request));
        flushIfNeeded();
        return f;
    }

    @SneakyThrows
    private void flushIfNeeded() {
        if (requests.size() >= bulkActions) {
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
        final List<CompletableFuture<Void>> futures = doFlush(batch);
        final CompletableFuture<Void> future = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[futures.size()]));
        future.whenComplete((v, t) -> semaphore.release());
        future.join();
        lastFlushTS = System.currentTimeMillis();
    }

    private List<CompletableFuture<Void>> doFlush(final List<Holder> batch) {
        log.debug("Executing bulk with {} requests", batch.size());
        if (batch.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            int bufferOfBytes = 0;
            Codec codec = es.get().version().get().codec();
            final List<byte[]> bs = new ArrayList<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            List<ByteBuf> byteBufList = new ArrayList<>();
            for (final Holder holder : batch) {
                byte[] bytes = codec.encode(holder.request);
                bs.add(bytes);
                bs.add("\n".getBytes());
                bufferOfBytes += bytes.length + 1;
                if (bufferOfBytes >= batchOfBytes) {
                    final ByteBuf content = Unpooled.wrappedBuffer(bs.toArray(new byte[0][]));
                    byteBufList.add(content);
                    bs.clear();
                    bufferOfBytes = 0;
                }
            }
            if (CollectionUtils.isNotEmpty(bs)) {
                final ByteBuf content = Unpooled.wrappedBuffer(bs.toArray(new byte[0][]));
                byteBufList.add(content);
            }
            for (final ByteBuf content : byteBufList) {
                CompletableFuture<Void> future = es.get().version().thenCompose(v -> {
                    try {
                        final RequestFactory rf = v.requestFactory();
                        return es.get().client().execute(rf.bulk().bulk(content)).aggregate().thenAccept(response -> {
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
                             .forEach(it -> it.completeExceptionally((Throwable) exception));
                        log.error("Failed to execute requests in bulk", exception);
                    } else {
                        log.debug("Succeeded to execute {} requests in bulk", batch.size());
                        batch.stream().map(it -> it.future).forEach(it -> it.complete(null));
                    }
                });
                futures.add(future);
            }
            return futures;

        } catch (Exception e) {
            log.error("Failed to execute requests in bulk", e);
            return Collections.emptyList();
        }
    }

    @RequiredArgsConstructor
    static class Holder {
        private final CompletableFuture<Void> future;
        private final Object request;
    }

}
