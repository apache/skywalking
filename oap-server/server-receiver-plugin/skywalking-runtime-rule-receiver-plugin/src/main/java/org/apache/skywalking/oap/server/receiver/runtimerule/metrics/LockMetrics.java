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

package org.apache.skywalking.oap.server.receiver.runtimerule.metrics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * Observability wrapper around per-file lock acquires. Exposes two helpers:
 * <ul>
 *   <li>{@link #acquireForRest} — {@code tryLock(timeout)} + histogram-record wait time +
 *       WARN if above threshold + timeout counter on failure. REST path.</li>
 *   <li>{@link #tryAcquireForSyncTimer} — non-blocking {@code tryLock()} + skip counter on
 *       failure. dslManager sync-timer path.</li>
 * </ul>
 *
 * <p>Metric names (Prometheus-style, already scraped by the standard
 * {@link TelemetryModule} telemetry pipeline):
 * <ul>
 *   <li>{@code runtime_rule_lock_wait_seconds} — histogram of lock-wait duration on the
 *       REST path ({@code path=rest}). Sync-timer acquires are non-blocking (no wait time
 *       to observe), so there is no {@code path=sync-timer} variant.</li>
 *   <li>{@code runtime_rule_lock_hold_seconds} — histogram of lock-hold duration per path
 *       ({@code path=rest|sync-timer}). Use via {@link HistogramMetrics.Timer} /
 *       {@link #restHoldHistogram}.</li>
 *   <li>{@code runtime_rule_lock_contention_total} — counter of timed-out REST acquires +
 *       skipped sync-timer acquires, labeled by {@code path,outcome}.</li>
 * </ul>
 *
 * <p>Graceful degradation: if the telemetry module isn't wired (embedded test topology),
 * resolution returns null and all wrappers fall back to plain {@code ReentrantLock} calls
 * without recording anything. Tests can therefore construct these without wiring a full
 * telemetry pipeline.
 */
@Slf4j
public final class LockMetrics {

    private static final long REST_WAIT_WARN_THRESHOLD_MS = 1_000L;

    // Sync-timer path is non-blocking (tryLock() with no wait), so there is no wait
    // histogram for it — the only contention signal we surface for ticks is the skip counter.
    private final HistogramMetrics restWaitHistogram;
    private final HistogramMetrics restHoldHistogram;
    private final HistogramMetrics syncTimerHoldHistogram;
    private final CounterMetrics restTimeoutCounter;
    private final CounterMetrics syncTimerSkipCounter;

    public LockMetrics(final ModuleManager moduleManager) {
        final MetricsCreator mc = resolve(moduleManager);
        if (mc == null) {
            this.restWaitHistogram = null;
            this.restHoldHistogram = null;
            this.syncTimerHoldHistogram = null;
            this.restTimeoutCounter = null;
            this.syncTimerSkipCounter = null;
            log.info("runtime-rule lock metrics disabled — MetricsCreator not resolvable");
            return;
        }
        final MetricsTag.Keys pathKey = new MetricsTag.Keys("path");
        this.restWaitHistogram = mc.createHistogramMetric(
            "runtime_rule_lock_wait_seconds",
            "Per-file lock acquisition wait time on the REST path",
            pathKey, new MetricsTag.Values("rest"));
        this.restHoldHistogram = mc.createHistogramMetric(
            "runtime_rule_lock_hold_seconds",
            "Per-file lock hold duration on the REST path (full workflow)",
            pathKey, new MetricsTag.Values("rest"));
        this.syncTimerHoldHistogram = mc.createHistogramMetric(
            "runtime_rule_lock_hold_seconds",
            "Per-file lock hold duration on the dslManager sync-timer path (single apply)",
            pathKey, new MetricsTag.Values("sync-timer"));
        this.restTimeoutCounter = mc.createCounter(
            "runtime_rule_lock_contention_total",
            "Per-file lock contention events — REST timeouts + sync-timer skips",
            new MetricsTag.Keys("path", "outcome"),
            new MetricsTag.Values("rest", "timeout"));
        this.syncTimerSkipCounter = mc.createCounter(
            "runtime_rule_lock_contention_total",
            "Per-file lock contention events — REST timeouts + sync-timer skips",
            new MetricsTag.Keys("path", "outcome"),
            new MetricsTag.Values("sync-timer", "skipped"));
    }

    private static MetricsCreator resolve(final ModuleManager moduleManager) {
        if (moduleManager == null) {
            return null;
        }
        try {
            return moduleManager.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
        } catch (final Throwable t) {
            return null;
        }
    }

    /**
     * REST-path acquisition. Blocks up to {@code timeoutMs} using
     * {@link ReentrantLock#tryLock(long, TimeUnit)}. Returns true on acquire, false on
     * timeout. Records wait histogram for both outcomes; increments the timeout counter on
     * false; emits a WARN log line when an acquire took longer than
     * {@link #REST_WAIT_WARN_THRESHOLD_MS} — catches pathological waits even without
     * operators looking at the dashboard.
     */
    public boolean acquireForRest(final ReentrantLock lock, final long timeoutMs,
                                   final String catalog, final String name) {
        final long t0 = System.nanoTime();
        final boolean acquired;
        try {
            acquired = lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
        final long waitMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        if (restWaitHistogram != null) {
            restWaitHistogram.observe(waitMs / 1000.0d);
        }
        if (!acquired) {
            if (restTimeoutCounter != null) {
                restTimeoutCounter.inc();
            }
            log.info("runtime-rule lock TIMEOUT on REST path {}/{} after {} ms",
                catalog, name, waitMs);
            return false;
        }
        if (waitMs >= REST_WAIT_WARN_THRESHOLD_MS) {
            log.warn("runtime-rule lock SLOW on REST path {}/{} — waited {} ms before acquiring",
                catalog, name, waitMs);
        }
        return true;
    }

    /**
     * Sync-timer-path acquisition. Non-blocking {@link ReentrantLock#tryLock()}. Returns
     * true on acquire. On failure, increments the skip counter and returns false; caller
     * continues to the next file without waiting.
     */
    public boolean tryAcquireForSyncTimer(final ReentrantLock lock,
                                      final String catalog, final String name) {
        final boolean acquired = lock.tryLock();
        if (!acquired) {
            if (syncTimerSkipCounter != null) {
                syncTimerSkipCounter.inc();
            }
            log.debug("runtime-rule lock skipped on sync-timer path {}/{} — busy",
                catalog, name);
        }
        return acquired;
    }

    /** Start a timer that records lock-hold duration for the REST path when closed. */
    public HistogramMetrics.Timer startRestHoldTimer() {
        return restHoldHistogram == null ? NO_OP_TIMER : restHoldHistogram.createTimer();
    }

    /** Start a timer that records lock-hold duration for the sync-timer path when closed. */
    public HistogramMetrics.Timer startSyncTimerHoldTimer() {
        return syncTimerHoldHistogram == null ? NO_OP_TIMER : syncTimerHoldHistogram.createTimer();
    }

    /**
     * Null-object for the test / no-telemetry case. {@link HistogramMetrics.Timer} is an
     * AutoCloseable; closing the null-object just does nothing. Avoids having to null-check
     * at every call site.
     */
    private static final HistogramMetrics.Timer NO_OP_TIMER = new HistogramMetrics.Timer(null) {
        @Override
        public void close() {
            // no-op
        }
    };
}
