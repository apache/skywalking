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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LookupCacheTest {
    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(3);
    private static final Duration NOT_FOUND_RETRY = Duration.ofMinutes(1);
    private static final Duration EXPIRY = Duration.ofMinutes(21);
    /**
     * What the API server answers for a key whose read fails.
     */
    private static final String FAILS = "fails";

    private final AtomicLong nanos = new AtomicLong(TimeUnit.HOURS.toNanos(1));
    private final Ticker ticker = new Ticker() {
        @Override
        public long read() {
            return nanos.get();
        }
    };
    private final Queue<Runnable> readerThread = new ArrayDeque<>();
    private final Queue<Runnable> waitingReaderThread = new ArrayDeque<>();
    private final SerialReader serialReader = new SerialReader(readerThread::add, "background read queue");
    private final SerialReader waitingReader = new SerialReader(waitingReaderThread::add, "waiting read queue");
    private final Map<String, String> apiServer = new HashMap<>();
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger maxQueuedReads = new AtomicInteger(-1);
    private final LookupCache<String, String> cache = new LookupCache<>(
        this::readFromApiServer, REFRESH_INTERVAL, NOT_FOUND_RETRY, EXPIRY, maxQueuedReads::get, maxQueuedReads::get,
        waitingReader, serialReader, ticker);

    @Test
    public void aLookupDoesNotWaitAndTheReadServesTheNextLookups() {
        apiServer.put("10.0.0.1", "web-1");

        assertEquals(Optional.empty(), cache.find("10.0.0.1"));
        assertEquals(Optional.empty(), cache.find("10.0.0.1"));
        assertEquals(0, reads.get());

        runReads();
        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));
        assertEquals(1, reads.get());
    }

    @Test
    public void anEntryUsedAfterTheRefreshIntervalIsReadAgainOnceWhileTheLookupsKeepGettingIt() {
        readFirst("10.0.0.1", "web-1");
        advance(REFRESH_INTERVAL);
        apiServer.put("10.0.0.1", "web-2");

        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));
        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));
        runReads();

        assertEquals(Optional.of("web-2"), cache.find("10.0.0.1"));
        assertEquals(2, reads.get());
    }

    @Test
    public void aReadAgainOfTheSameDataWritesTheEntryAgainSoAnEntryInUseDoesNotExpire() {
        readFirst("10.0.0.1", "web-1");
        advance(REFRESH_INTERVAL);
        cache.find("10.0.0.1");
        runReads();

        // Twenty minutes after the second write, twenty-three after the first.
        advance(Duration.ofMinutes(20));
        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));
    }

    @Test
    public void anEntryExpiresTheExpiryAfterItsLastWrite() {
        readFirst("10.0.0.1", "web-1");
        advance(EXPIRY.minusSeconds(1));
        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));

        advance(Duration.ofSeconds(1));
        assertEquals(Optional.empty(), cache.find("10.0.0.1"));
    }

    @Test
    public void aReadAgainThatFindsNothingDoesNotWriteSoTheEntryExpires() {
        readFirst("10.0.0.1", "web-1");
        advance(REFRESH_INTERVAL);
        apiServer.remove("10.0.0.1");
        cache.find("10.0.0.1");
        runReads();

        advance(Duration.ofMinutes(2));
        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));
        runReads();
        assertEquals(2, reads.get());

        // A refresh interval after the read again found nothing, a use reads again.
        advance(Duration.ofMinutes(1));
        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));
        runReads();
        assertEquals(3, reads.get());

        // The expiry after the only write.
        advance(EXPIRY.minus(REFRESH_INTERVAL.multipliedBy(2)));
        assertEquals(Optional.empty(), cache.find("10.0.0.1"));
    }

    @Test
    public void aFailedReadAgainKeepsTheDataAndIsRetriedOnAUseARefreshIntervalLater() {
        readFirst("10.0.0.1", "web-1");
        advance(REFRESH_INTERVAL);
        apiServer.put("10.0.0.1", FAILS);
        cache.find("10.0.0.1");
        runReads();
        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));

        advance(Duration.ofMinutes(2));
        cache.find("10.0.0.1");
        runReads();
        assertEquals(2, reads.get());

        advance(Duration.ofMinutes(1));
        apiServer.put("10.0.0.1", "web-2");
        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));
        runReads();
        assertEquals(Optional.of("web-2"), cache.find("10.0.0.1"));
        assertEquals(3, reads.get());
    }

    @Test
    public void aKeyHasOnePendingReadSoALookupAfterItsEntryExpiredSharesTheReadAgain() {
        maxQueuedReads.set(1);
        readFirst("10.0.0.1", "web-1");
        advance(REFRESH_INTERVAL);
        cache.find("10.0.0.1");
        advance(EXPIRY);
        apiServer.put("10.0.0.1", "web-2");

        // Expired, while its read again is still queued: the lookup shares it, the queue keeps one slot taken.
        assertEquals(Optional.empty(), cache.find("10.0.0.1"));
        cache.find("10.0.0.2");
        runReads();

        assertEquals(Optional.of("web-2"), cache.find("10.0.0.1"));
        assertEquals(2, reads.get());
    }

    @Test
    public void aTimedLookupWhoseReadTheFullQueueRefusesDoesNotWait() {
        maxQueuedReads.set(0);

        assertTimeoutPreemptively(
            Duration.ofSeconds(5), () -> assertEquals(Optional.empty(), cache.find("10.0.0.1", 10_000)));
        assertTrue(readerThread.isEmpty());
    }

    @Test
    public void aFirstReadThatFindsNothingIsCachedSoTheKeyIsNotReadOnEveryLookup() {
        cache.find("8.8.8.8");
        runReads();

        assertEquals(Optional.empty(), cache.find("8.8.8.8"));
        assertEquals(Optional.empty(), cache.find("8.8.8.8"));
        runReads();
        assertEquals(1, reads.get());
    }

    @Test
    public void findingNothingIsReadAgainOnAUseTheNotFoundRetryPeriodLater() {
        cache.find("10.0.0.9");
        runReads();

        advance(NOT_FOUND_RETRY.minusSeconds(1));
        cache.find("10.0.0.9");
        runReads();
        assertEquals(1, reads.get());

        // Nothing again: written again, so read again only a period later.
        advance(Duration.ofSeconds(1));
        assertEquals(Optional.empty(), cache.find("10.0.0.9"));
        runReads();
        assertEquals(2, reads.get());
        advance(NOT_FOUND_RETRY.minusSeconds(1));
        cache.find("10.0.0.9");
        runReads();
        assertEquals(2, reads.get());

        // A pod new to the API server.
        advance(Duration.ofSeconds(1));
        apiServer.put("10.0.0.9", "new-pod");
        assertEquals(Optional.empty(), cache.find("10.0.0.9"));
        runReads();
        assertEquals(Optional.of("new-pod"), cache.find("10.0.0.9"));
    }

    @Test
    public void aFailedReadIsRetriedOnAUseTheRefreshIntervalLaterNotTheNotFoundRetryPeriod() {
        apiServer.put("10.0.0.1", FAILS);
        cache.find("10.0.0.1");
        runReads();

        advance(NOT_FOUND_RETRY);
        assertEquals(Optional.empty(), cache.find("10.0.0.1"));
        runReads();
        assertEquals(1, reads.get());

        advance(REFRESH_INTERVAL.minus(NOT_FOUND_RETRY));
        apiServer.put("10.0.0.1", "web-1");
        assertEquals(Optional.empty(), cache.find("10.0.0.1"));
        runReads();
        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));
        assertEquals(2, reads.get());
    }

    @Test
    public void aReadAgainOfAKeyWithoutDataWritesThatItFailed() {
        cache.find("10.0.0.1");
        runReads();
        advance(NOT_FOUND_RETRY);
        apiServer.put("10.0.0.1", FAILS);
        cache.find("10.0.0.1");
        runReads();
        assertEquals(2, reads.get());

        // Failed now: retried a refresh interval later, not a not-found retry period.
        advance(NOT_FOUND_RETRY);
        cache.find("10.0.0.1");
        runReads();
        assertEquals(2, reads.get());
        advance(REFRESH_INTERVAL.minus(NOT_FOUND_RETRY));
        cache.find("10.0.0.1");
        runReads();
        assertEquals(3, reads.get());
    }

    @Test
    public void aReadAgainRefusedAsTheLaneIsFullIsQueuedOnALaterUse() {
        maxQueuedReads.set(1);
        readFirst("10.0.0.1", "web-1");
        advance(REFRESH_INTERVAL);
        apiServer.put("10.0.0.1", "web-2");
        cache.find("10.0.0.2");
        // The lane holds the read of 10.0.0.2: the read again of 10.0.0.1 is refused, and the entry released.
        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));
        runReads();
        assertEquals(2, reads.get());

        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));
        runReads();
        assertEquals(Optional.of("web-2"), cache.find("10.0.0.1"));
        assertEquals(3, reads.get());
    }

    @Test
    public void awaitingWaitsForThePendingReadOnly() {
        final ExecutorService readers = Executors.newSingleThreadExecutor();
        final CountDownLatch release = new CountDownLatch(1);
        try {
            final LookupCache<String, String> timed = new LookupCache<>(key -> {
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "web-1";
            }, REFRESH_INTERVAL, NOT_FOUND_RETRY, EXPIRY, () -> -1, () -> -1, new SerialReader(readers, "waiting"),
                new SerialReader(readers, "background"), ticker);

            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> timed.awaitRead("10.0.0.1", 10_000, 10_000));
            assertEquals(Optional.empty(), timed.find("10.0.0.1", 0));
            final long start = System.nanoTime();
            timed.awaitRead("10.0.0.1", 10_000, 200);
            assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(200));
            // Queued longer ago than the timeout: already waited for, not waited for again.
            advance(Duration.ofSeconds(11));
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> timed.awaitRead("10.0.0.1", 10_000, 10_000));

            release.countDown();
            timed.awaitRead("10.0.0.1", 60_000, 10_000);
            assertEquals(Optional.of("web-1"), timed.find("10.0.0.1"));
        } finally {
            release.countDown();
            readers.shutdownNow();
        }
    }

    @Test
    public void aCachedKeyIsNotWaitedForWhileItIsReadAgain() {
        readFirst("10.0.0.1", "web-1");
        advance(REFRESH_INTERVAL);
        cache.queueForWaiting("10.0.0.1", 10_000);
        assertEquals(1, readerThread.size());

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> cache.awaitRead("10.0.0.1", 10_000, 10_000));
    }

    @Test
    public void aKeyIsWaitedForTheTimeoutAtMostInAllEvenWhenQueuedAgainWithinTwiceIt() {
        final ExecutorService readers = Executors.newSingleThreadExecutor();
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger waitingLaneSize = new AtomicInteger(0);
        try {
            final LookupCache<String, String> timed = new LookupCache<>(key -> {
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "web-1";
            }, REFRESH_INTERVAL, NOT_FOUND_RETRY, EXPIRY, waitingLaneSize::get, () -> -1,
                new SerialReader(readers, "waiting"), new SerialReader(readers, "background"), ticker);

            // Refused, as the lane is full: nothing to wait for.
            timed.queueForWaiting("10.0.0.1", 2_000);
            timed.awaitRead("10.0.0.1", 2_000, 2_000);

            // Queued again by the next step 2.5 s later: waited for since the first step, so not at all.
            advance(Duration.ofMillis(2_500));
            waitingLaneSize.set(-1);
            timed.queueForWaiting("10.0.0.1", 2_000);
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> timed.awaitRead("10.0.0.1", 2_000, 10_000));
        } finally {
            release.countDown();
            readers.shutdownNow();
        }
    }

    @Test
    public void aKeyQueuedAgainLaterThanTwiceTheTimeoutIsWaitedForAgain() {
        final ExecutorService readers = Executors.newSingleThreadExecutor();
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger waitingLaneSize = new AtomicInteger(0);
        try {
            final LookupCache<String, String> timed = new LookupCache<>(key -> {
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "web-1";
            }, REFRESH_INTERVAL, NOT_FOUND_RETRY, EXPIRY, waitingLaneSize::get, () -> -1,
                new SerialReader(readers, "waiting"), new SerialReader(readers, "background"), ticker);

            timed.queueForWaiting("10.0.0.1", 2_000);
            advance(Duration.ofSeconds(5));
            waitingLaneSize.set(-1);
            timed.queueForWaiting("10.0.0.1", 2_000);
            final long start = System.nanoTime();
            timed.awaitRead("10.0.0.1", 2_000, 200);
            assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(200));
        } finally {
            release.countDown();
            readers.shutdownNow();
        }
    }

    @Test
    public void theExpiryIsLongerThanTheRefreshInterval() {
        assertThrows(IllegalArgumentException.class, () -> new LookupCache<String, String>(
            this::readFromApiServer, REFRESH_INTERVAL, NOT_FOUND_RETRY, REFRESH_INTERVAL, () -> -1, () -> -1));
    }

    @Test
    public void aKeyFindingTheQueueFullIsReadOnALaterLookup() {
        maxQueuedReads.set(1);
        apiServer.put("10.0.0.1", "web-1");
        apiServer.put("10.0.0.2", "web-2");

        cache.find("10.0.0.1");
        cache.find("10.0.0.2");
        runReads();
        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1"));
        assertEquals(Optional.empty(), cache.find("10.0.0.2"));

        runReads();
        assertEquals(Optional.of("web-2"), cache.find("10.0.0.2"));
    }

    @Test
    public void aTimedLookupWaitsForTheReadOfAKeyNotReadYet() {
        final ExecutorService readers = Executors.newSingleThreadExecutor();
        try {
            apiServer.put("10.0.0.1", "web-1");
            final LookupCache<String, String> timed = new LookupCache<>(
                this::readFromApiServer, REFRESH_INTERVAL, NOT_FOUND_RETRY, EXPIRY, () -> -1, () -> -1,
                new SerialReader(readers, "waiting"), new SerialReader(readers, "background"), ticker);

            assertEquals(Optional.of("web-1"), timed.find("10.0.0.1", 10_000));
        } finally {
            readers.shutdownNow();
        }
    }

    @Test
    public void aTimedLookupWaitsAtMostTheTimeoutSinceTheReadWasQueued() {
        final ExecutorService readers = Executors.newSingleThreadExecutor();
        final CountDownLatch release = new CountDownLatch(1);
        try {
            final LookupCache<String, String> timed = new LookupCache<>(key -> {
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "web-1";
            }, REFRESH_INTERVAL, NOT_FOUND_RETRY, EXPIRY, () -> -1, () -> -1, new SerialReader(readers, "waiting"),
                new SerialReader(readers, "background"), ticker);

            assertEquals(Optional.empty(), timed.find("10.0.0.1", 10));
            // Queued more than the timeout ago: a later lookup returns without waiting again.
            advance(Duration.ofSeconds(11));
            assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> assertEquals(Optional.empty(), timed.find("10.0.0.1", 10_000)));
        } finally {
            release.countDown();
            readers.shutdownNow();
        }
    }

    @Test
    public void aWaitingLookupDoesNotQueueBehindTheReadsNobodyWaitsFor() {
        apiServer.put("10.0.0.9", "mal-pod");
        for (int i = 0; i < 100; i++) {
            cache.find("10.1.0." + i);
        }

        // Its read goes to the waiting lane, which runs it while the background lane still holds 100 reads.
        cache.find("10.0.0.9", 0);
        assertEquals(1, waitingReaderThread.size());
        waitingReaderThread.poll().run();
        assertEquals(Optional.of("mal-pod"), cache.find("10.0.0.9"));
        assertEquals(1, reads.get());
    }

    @Test
    public void aWaitingLookupSharesTheReadAlreadyQueuedForItsKeyInTheOtherLane() {
        apiServer.put("10.0.0.1", "web-1");
        cache.find("10.0.0.1");

        assertEquals(Optional.empty(), cache.find("10.0.0.1", 0));
        assertTrue(waitingReaderThread.isEmpty());
        runReads();
        assertEquals(Optional.of("web-1"), cache.find("10.0.0.1", 0));
        assertEquals(1, reads.get());
    }

    @Test
    public void aWaitingLookupAfterItsEntryExpiredSharesTheQueuedReadAgain() {
        readFirst("10.0.0.1", "web-1");
        advance(REFRESH_INTERVAL);
        cache.find("10.0.0.1");
        advance(EXPIRY);
        apiServer.put("10.0.0.1", "web-2");

        assertEquals(Optional.empty(), cache.find("10.0.0.1", 0));
        assertTrue(waitingReaderThread.isEmpty());
        runReads();
        assertEquals(Optional.of("web-2"), cache.find("10.0.0.1", 0));
        assertEquals(2, reads.get());
    }

    @Test
    public void eachLaneHasItsOwnLimit() {
        final LookupCache<String, String> limited = new LookupCache<>(
            this::readFromApiServer, REFRESH_INTERVAL, NOT_FOUND_RETRY, EXPIRY, () -> 1, () -> 0, waitingReader,
            serialReader, ticker);

        limited.find("10.0.0.1");
        limited.find("10.0.0.2", 0);
        assertTrue(readerThread.isEmpty());
        assertEquals(1, waitingReaderThread.size());
    }

    @Test
    public void aFaultOfTheReaderCountsAsAFailedRead() {
        final AtomicInteger faults = new AtomicInteger();
        final LookupCache<String, String> faulty = new LookupCache<>(key -> {
            faults.incrementAndGet();
            throw new IllegalStateException("bug");
        }, REFRESH_INTERVAL, NOT_FOUND_RETRY, EXPIRY, () -> -1, () -> -1, waitingReader, serialReader, ticker);

        faulty.find("10.0.0.1");
        runReads();
        advance(NOT_FOUND_RETRY);
        assertEquals(Optional.empty(), faulty.find("10.0.0.1"));
        assertTrue(readerThread.isEmpty());
        advance(REFRESH_INTERVAL.minus(NOT_FOUND_RETRY));
        faulty.find("10.0.0.1");
        runReads();
        assertEquals(2, faults.get());
    }

    private String readFromApiServer(final String key) {
        reads.incrementAndGet();
        final String value = apiServer.get(key);
        if (FAILS.equals(value)) {
            throw new KubernetesReadFailedException("list pods", "code=503");
        }
        return value;
    }

    private void readFirst(final String key, final String value) {
        apiServer.put(key, value);
        cache.find(key);
        runReads();
    }

    private void runReads() {
        for (Runnable task = readerThread.poll(); task != null; task = readerThread.poll()) {
            task.run();
        }
    }

    private void advance(final Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }
}
