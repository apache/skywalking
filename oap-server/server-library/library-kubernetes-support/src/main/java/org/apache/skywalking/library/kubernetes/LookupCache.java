/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.library.kubernetes;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Caches what lookups find in the Kubernetes metadata, and reads it in the background, so a lookup does not wait for
 * the API server, or waits only up to the time it asks for.
 * <ul>
 *     <li>A key that is not cached is read once in the background; the lookups of the key share that read. A key has
 *     one pending read at most. The reads of every cache are queued to two lanes, each running its reads one at a
 *     time: one for the first reads a waiting lookup queues, and one for the rest. A lookup shares any read already
 *     pending for its key, including one in the other lane.</li>
 *     <li>What a read finds is cached, and the first use of the entry from a set time on reads the key again in the
 *     background, once, while lookups keep getting the entry:
 *     <ul>
 *         <li>data: {@code refreshPeriod} after it was written. A read again returning data, the same or not, writes
 *         the entry again; one finding nothing or failing does not, and the key is read again on a use
 *         {@code refreshPeriod} later.</li>
 *         <li>nothing found, such as for an IP outside the cluster: {@code notFoundRetryPeriod} after it was written,
 *         as the key may be new to the API server or to the lists the reader matches it in.</li>
 *         <li>a failed read, such as for a 403 or the API server not answering: {@code refreshPeriod} after it
 *         failed.</li>
 *     </ul>
 *     A read again of an entry without data writes what it finds, or that it failed.</li>
 *     <li>An entry expires {@code expiry} after it was last written: an entry that is not used, as it is not read
 *     again, and data whose reads again find nothing or fail, as they do not write it.</li>
 * </ul>
 *
 * @param <K> the key
 * @param <V> the data found for a key
 */
@Slf4j
public final class LookupCache<K, V> {
    /**
     * The lane of the first reads queued by waiting lookups, apart from the reads nobody waits for.
     */
    private static final SerialReader WAITING_READS =
        new SerialReader(BackgroundExecutors.create("K8sLookup-waiting"), "waiting read queue");
    /**
     * The lane of the reads queued by lookups that do not wait, and of every read again.
     */
    private static final SerialReader BACKGROUND_READS =
        new SerialReader(BackgroundExecutors.create("K8sLookup"), "background read queue");

    private final Function<K, V> reader;
    private final long refreshNanos;
    private final long notFoundRetryNanos;
    private final IntSupplier maxWaitingReads;
    private final IntSupplier maxBackgroundReads;
    private final SerialReader waitingReads;
    private final SerialReader backgroundReads;
    private final Ticker ticker;
    private final Cache<K, Entry<V>> entries;
    private final ConcurrentMap<K, PendingRead<V>> pendingReads = new ConcurrentHashMap<>();
    /**
     * When waiting callers started waiting for a key, kept for {@link #queueForWaiting}; dropped a refresh period
     * after, long after it no longer counts.
     */
    private final Cache<K, Long> waitStarts;

    /**
     * @param reader              finds the data of a key: returns null when nothing is found, and throws
     *                            {@link KubernetesReadFailedException} when the read failed; it runs in the
     *                            background
     * @param refreshPeriod       how long data is used before it is read again, and a failed read waits before the
     *                            key is read again
     * @param notFoundRetryPeriod how long finding nothing is used before the key is read again
     * @param expiry              how long after it was last written an entry expires; longer than
     *                            {@code refreshPeriod}
     * @param maxWaitingReads     the most reads queued at once in the lane of the reads lookups wait for, or a negative
     *                            number for no limit; a key not queued as the lane is full is read on a later lookup
     * @param maxBackgroundReads  the same for the lane of the other reads
     */
    public LookupCache(final Function<K, V> reader, final Duration refreshPeriod, final Duration notFoundRetryPeriod,
                       final Duration expiry, final IntSupplier maxWaitingReads, final IntSupplier maxBackgroundReads) {
        this(reader, refreshPeriod, notFoundRetryPeriod, expiry, maxWaitingReads, maxBackgroundReads,
             WAITING_READS, BACKGROUND_READS, Ticker.systemTicker());
    }

    LookupCache(final Function<K, V> reader, final Duration refreshPeriod, final Duration notFoundRetryPeriod,
                final Duration expiry, final IntSupplier maxWaitingReads, final IntSupplier maxBackgroundReads,
                final SerialReader waitingReads, final SerialReader backgroundReads, final Ticker ticker) {
        if (expiry.compareTo(refreshPeriod) <= 0) {
            throw new IllegalArgumentException(
                "The expiry " + expiry + " must be longer than the refresh period " + refreshPeriod);
        }
        this.reader = reader;
        this.refreshNanos = refreshPeriod.toNanos();
        this.notFoundRetryNanos = notFoundRetryPeriod.toNanos();
        this.maxWaitingReads = maxWaitingReads;
        this.maxBackgroundReads = maxBackgroundReads;
        this.waitingReads = waitingReads;
        this.backgroundReads = backgroundReads;
        this.ticker = ticker;
        this.entries = CacheBuilder.newBuilder()
                                   .expireAfterWrite(expiry)
                                   .ticker(ticker)
                                   .build();
        this.waitStarts = CacheBuilder.newBuilder()
                                      .expireAfterWrite(refreshPeriod)
                                      .ticker(ticker)
                                      .build();
    }

    /**
     * Never waits: a key not cached yet is found by a later lookup, once its read has completed.
     */
    public Optional<V> find(final K key) {
        final Entry<V> entry = entries.getIfPresent(key);
        if (entry != null) {
            return use(key, entry);
        }
        final PendingRead<V> read = readInBackground(key, null, backgroundReads, maxBackgroundReads);
        return read.result.isDone() ? Optional.ofNullable(read.result.join()) : Optional.empty();
    }

    /**
     * Waits for a key not cached yet up to {@code timeoutMillis} since its read was queued, so lookups of a key whose
     * read is slow do not each wait the whole timeout. A cached key, whether data, nothing found or a failed read, is
     * answered at once.
     */
    public Optional<V> find(final K key, final long timeoutMillis) {
        final Entry<V> entry = entries.getIfPresent(key);
        if (entry != null) {
            return use(key, entry);
        }
        final PendingRead<V> read = readInBackground(key, null, waitingReads, maxWaitingReads);
        final long remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis) - (ticker.read() - read.queuedNanos);
        if (remainingNanos <= 0 && !read.result.isDone()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(read.result.get(Math.max(remainingNanos, 0), TimeUnit.NANOSECONDS));
        } catch (final TimeoutException | ExecutionException e) {
            return Optional.empty();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Starts the wait of a caller that then waits with {@link #awaitRead}: queues the read of {@code key} in the lane
     * of the reads waited for, unless it is cached or pending, and records when waiting for the key started. The
     * start of an earlier wait within twice {@code timeoutMillis} is kept, so the callers looking up the same keys one
     * after another, such as the steps over one batch of data, wait for a key {@code timeoutMillis} at most in all,
     * even when its read is queued again meanwhile, as its lane was full or its entry expired.
     */
    public void queueForWaiting(final K key, final long timeoutMillis) {
        final long now = ticker.read();
        final long windowNanos = 2 * TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        waitStarts.asMap().compute(key, (k, earlier) -> earlier == null || now - earlier >= windowNanos ? now : earlier);
        find(key, 0);
    }

    /**
     * Waits for the read pending for {@code key} not cached, such as the one {@link #queueForWaiting} queued: up to
     * {@code timeoutMillis} since waiting for the key started, or since the read was queued if earlier, and
     * {@code maxWaitMillis} at most, so a caller looking up many keys, which queues all their reads first, waits for
     * them within one time limit. A cached key is not waited for, even while it is read again.
     */
    public void awaitRead(final K key, final long timeoutMillis, final long maxWaitMillis) {
        if (entries.getIfPresent(key) != null) {
            return;
        }
        final PendingRead<V> read = pendingReads.get(key);
        if (read == null) {
            return;
        }
        final Long waitStart = waitStarts.getIfPresent(key);
        final long since = waitStart == null ? read.queuedNanos : Math.min(waitStart, read.queuedNanos);
        final long waitNanos = Math.min(
            TimeUnit.MILLISECONDS.toNanos(maxWaitMillis),
            TimeUnit.MILLISECONDS.toNanos(timeoutMillis) - (ticker.read() - since)
        );
        if (waitNanos <= 0) {
            return;
        }
        try {
            read.result.get(waitNanos, TimeUnit.NANOSECONDS);
        } catch (final TimeoutException | ExecutionException e) {
            // Still read in the background, for a later lookup.
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Optional<V> use(final K key, final Entry<V> entry) {
        if (isDue(entry) && entry.reading.compareAndSet(false, true)) {
            // Checked again once claimed: a read of the entry completing meanwhile may have moved its next read.
            if (!isDue(entry)) {
                entry.reading.set(false);
            } else if (readInBackground(key, entry, backgroundReads, maxBackgroundReads).readAgain != entry) {
                // Joined a read pending for the key that is not for this entry, which does not release it.
                entry.reading.set(false);
            }
        }
        return Optional.ofNullable(entry.value);
    }

    private boolean isDue(final Entry<V> entry) {
        return ticker.read() - entry.nextReadNanos >= 0;
    }

    /**
     * Queues a read of {@code key}, unless one is pending: a key has one pending read at most, whether it reads the
     * key for the first time or again, so a lookup after the entry expired shares the read its last use queued.
     *
     * @param readAgain the entry the read is for, or null when the key is not cached
     * @param lane      where a read not pending yet is queued
     * @param maxQueued the size of {@code lane}
     * @return the pending read of {@code key}; it completes with nothing at once when the lane is full
     */
    private PendingRead<V> readInBackground(final K key, final Entry<V> readAgain, final SerialReader lane,
                                            final IntSupplier maxQueued) {
        final PendingRead<V> existing = pendingReads.get(key);
        if (existing != null) {
            return existing;
        }
        final PendingRead<V> created = new PendingRead<>(readAgain, ticker.read());
        final PendingRead<V> raced = pendingReads.putIfAbsent(key, created);
        if (raced != null) {
            return raced;
        }
        // A read completing between the lookup's miss and this registration has written the entry.
        final Entry<V> written = readAgain == null ? entries.getIfPresent(key) : null;
        if (written != null) {
            created.result.complete(written.value);
            pendingReads.remove(key, created);
            return created;
        }
        if (!lane.submit(() -> runRead(key, created), maxQueued.getAsInt())) {
            // Lookups sharing this read get nothing now, and a later lookup queues the key again.
            created.result.complete(null);
            pendingReads.remove(key, created);
            if (readAgain != null) {
                readAgain.reading.set(false);
            }
        }
        return created;
    }

    private void runRead(final K key, final PendingRead<V> pendingRead) {
        V value = null;
        boolean failed = false;
        try {
            value = reader.apply(key);
        } catch (final KubernetesReadFailedException e) {
            // Summarized by KubernetesApiReads.
            failed = true;
        } catch (final RuntimeException | LinkageError e) {
            log.warn("Failed to read {}.", key, e);
            failed = true;
        }
        final long now = ticker.read();
        final Entry<V> readAgain = pendingRead.readAgain;
        boolean releaseReadAgain = false;
        if (value != null) {
            // The only pending read of the key: no newer data can have been written meanwhile.
            entries.put(key, new Entry<>(value, now + refreshNanos));
        } else if (readAgain != null && readAgain.value != null && entries.getIfPresent(key) == readAgain) {
            // Data is not written by a read again finding nothing or failing, so it expires unless a later read
            // finds data.
            readAgain.nextReadNanos = now + refreshNanos;
            releaseReadAgain = true;
        } else {
            final Entry<V> nothing = new Entry<>(null, now + (failed ? refreshNanos : notFoundRetryNanos));
            // Replaces the entry without data the read was for, and is cached for a key not cached any more; it never
            // replaces an entry written meanwhile.
            if (readAgain == null || !entries.asMap().replace(key, readAgain, nothing)) {
                entries.asMap().putIfAbsent(key, nothing);
            }
        }
        // Written before the read is released, so a lookup finds either the entry or the read.
        pendingRead.result.complete(value);
        pendingReads.remove(key, pendingRead);
        if (releaseReadAgain) {
            // After the read is released, so a lookup claiming the entry from now on queues a read of its own instead
            // of joining this one, which would leave the entry claimed.
            readAgain.reading.set(false);
        }
    }

    private static final class Entry<V> {
        /**
         * Null when the read found nothing or failed.
         */
        private final V value;
        /**
         * The first use from this time on reads the key again.
         */
        private volatile long nextReadNanos;
        private final AtomicBoolean reading = new AtomicBoolean();

        private Entry(final V value, final long nextReadNanos) {
            this.value = value;
            this.nextReadNanos = nextReadNanos;
        }
    }

    private static final class PendingRead<V> {
        private final CompletableFuture<V> result = new CompletableFuture<>();
        /**
         * The entry this read is for, or null when the key was not cached.
         */
        private final Entry<V> readAgain;
        private final long queuedNanos;

        private PendingRead(final Entry<V> readAgain, final long queuedNanos) {
            this.readAgain = readAgain;
            this.queuedNanos = queuedNanos;
        }
    }
}
