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

package org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.parser;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter for per-log parse-failure WARNs. A high-volume layer can fail parsing on
 * every log, and an unbounded per-log WARN would flood the OAP log. At most one WARN is
 * emitted per interval per limiter instance (each parser holds its own); the number of
 * failures suppressed in between is reported with the next emitted WARN so no failure
 * goes uncounted. Intervals are measured on the monotonic clock, so wall-clock
 * adjustments can neither over-suppress nor over-emit.
 *
 * <p>Lock-free: the failure path is ingest-hot, so window transitions race on a CAS
 * instead of a mutex. A failure racing the window boundary may be attributed to the
 * next report rather than the current one; the total is never lost.
 */
public final class ParseFailureWarnLimiter {
    /** Shared default: one WARN per minute per parser. */
    public static final long DEFAULT_INTERVAL_MS = 60_000L;

    private final long intervalNanos;
    private final AtomicLong lastEmitNanos;
    private final AtomicLong suppressed = new AtomicLong();

    public ParseFailureWarnLimiter(final long intervalMs) {
        this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMs);
        // Back-date so the first failure always emits.
        this.lastEmitNanos = new AtomicLong(System.nanoTime() - this.intervalNanos);
    }

    /**
     * @return the number of failures suppressed since the previously emitted WARN when a
     *         WARN should be emitted now, or {@code -1} when this failure should be
     *         suppressed
     */
    public long acquire() {
        final long nowNanos = System.nanoTime();
        final long lastNanos = lastEmitNanos.get();
        if (nowNanos - lastNanos >= intervalNanos
            && lastEmitNanos.compareAndSet(lastNanos, nowNanos)) {
            return suppressed.getAndSet(0);
        }
        suppressed.incrementAndGet();
        return -1;
    }
}
