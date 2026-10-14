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

import io.fabric8.kubernetes.client.utils.Utils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Logs repeated failures as periodic summaries instead of one line per attempt. Failures are recorded per key, such
 * as a resource kind or a hostname, and a key stays failing until it succeeds. The first summary is logged
 * {@link #FIRST_SUMMARY_DELAY_MILLIS} after the first failure, so failures starting together share a line, and then
 * every {@link #SUMMARY_INTERVAL_MILLIS} while any key is failing, listing at most {@link #MAX_LISTED_KEYS} keys by
 * the failures since the previous summary. Once every failing key has succeeded, one recovery line is logged, but only
 * if a summary was logged, so a short failure logs nothing. Recording never blocks: the lines are written by a
 * background thread, outside the lock the recording threads take, in the order they were decided.
 */
public final class FailureSummaryLogger {
    static final long FIRST_SUMMARY_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(1);
    static final long SUMMARY_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(5);
    static final int MAX_LISTED_KEYS = 10;

    private static final ExecutorService OUTPUT = BackgroundExecutors.create("K8sFailureSummary");

    /**
     * Runs a task once after a delay, which may be zero, off the caller's thread. Tasks may run concurrently.
     */
    interface Scheduler {
        void schedule(Runnable task, long delayMillis);
    }

    private final String subject;
    private final String consequence;
    private final Consumer<String> failureLog;
    private final Consumer<String> recoveryLog;
    private final LongSupplier clock;
    private final Scheduler scheduler;

    private final Map<String, Failures> failingKeys = new HashMap<>();
    private long windowStartMillis;
    /**
     * When the pending summary is due; a summary is pending while any key is failing.
     */
    private long nextSummaryMillis;
    /**
     * Identifies the pending summary; a scheduled summary of an earlier generation does nothing.
     */
    private long generation;
    private boolean summaryLogged;
    /**
     * Writes of the decided lines, added under the lock in the order they were decided, and run in that order by
     * whichever {@link #writePendingLines()} gets them first.
     */
    private final Queue<Runnable> pendingLines = new ConcurrentLinkedQueue<>();
    private final Object outputLock = new Object();

    /**
     * @param subject     what fails, e.g. {@code Kubernetes API reads}
     * @param consequence what the failures mean for the reader, e.g. {@code lookups use the data last read, if any}
     * @param failureLog  where summaries go, e.g. {@code log::error}
     * @param recoveryLog where the recovery line goes, e.g. {@code log::info}
     */
    public FailureSummaryLogger(final String subject, final String consequence,
                                final Consumer<String> failureLog, final Consumer<String> recoveryLog) {
        // A monotonic clock: a wall-clock adjustment must not move a summary's deadline.
        this(subject, consequence, failureLog, recoveryLog, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()),
             (task, delayMillis) -> Utils.schedule(OUTPUT, task, delayMillis, TimeUnit.MILLISECONDS));
    }

    FailureSummaryLogger(final String subject, final String consequence, final Consumer<String> failureLog,
                         final Consumer<String> recoveryLog, final LongSupplier clock, final Scheduler scheduler) {
        this.subject = subject;
        this.consequence = consequence;
        this.failureLog = failureLog;
        this.recoveryLog = recoveryLog;
        this.clock = clock;
        this.scheduler = scheduler;
    }

    /**
     * @param key   a bounded identity of what failed, such as a resource kind or a hostname
     * @param cause a bounded description, such as {@code code=403} or {@code type=ConnectException}
     */
    public synchronized void recordFailure(final String key, final String cause) {
        if (failingKeys.isEmpty()) {
            final long now = clock.getAsLong();
            windowStartMillis = now;
            scheduleSummary(now, FIRST_SUMMARY_DELAY_MILLIS);
        }
        failingKeys.computeIfAbsent(key, k -> new Failures()).add(cause);
    }

    public synchronized void recordSuccess(final String key) {
        if (failingKeys.remove(key) != null && failingKeys.isEmpty()) {
            if (summaryLogged) {
                final String line = subject + " recovered.";
                write(() -> recoveryLog.accept(line));
            }
            summaryLogged = false;
            generation++;
        }
    }

    synchronized void logIfDue() {
        summarizeIfDue(generation, false);
    }

    private synchronized void summarizeIfDue(final long scheduledGeneration, final boolean rescheduleIfEarly) {
        if (scheduledGeneration != generation || failingKeys.isEmpty()) {
            return;
        }
        final long now = clock.getAsLong();
        final long remainingMillis = nextSummaryMillis - now;
        if (remainingMillis > 0) {
            if (rescheduleIfEarly) {
                scheduler.schedule(() -> summarizeIfDue(scheduledGeneration, true), remainingMillis);
            }
            return;
        }
        final List<Map.Entry<String, Failures>> byCount =
            failingKeys.entrySet()
                       .stream()
                       .sorted(Comparator.comparingInt(
                           (Map.Entry<String, Failures> it) -> it.getValue().count).reversed()
                                         .thenComparing(Map.Entry::getKey))
                       .collect(Collectors.toList());
        final long minutes = Math.max(1, TimeUnit.MILLISECONDS.toMinutes(now - windowStartMillis));
        final StringBuilder line = new StringBuilder(subject)
            .append(" failed for ").append(byCount.size()).append(byCount.size() == 1 ? " target" : " targets")
            .append(" in the last ").append(minutes).append(minutes == 1 ? " minute, " : " minutes, ")
            .append(consequence).append(": ");
        for (int i = 0; i < Math.min(MAX_LISTED_KEYS, byCount.size()); i++) {
            final Map.Entry<String, Failures> entry = byCount.get(i);
            if (i > 0) {
                line.append(", ");
            }
            line.append(entry.getKey()).append(' ').append(entry.getValue().lastCause)
                .append(" count=").append(entry.getValue().count);
        }
        if (byCount.size() > MAX_LISTED_KEYS) {
            line.append(" ... and ").append(byCount.size() - MAX_LISTED_KEYS).append(" more");
        }
        final String summary = line.toString();
        write(() -> failureLog.accept(summary));
        failingKeys.values().forEach(Failures::startWindow);
        summaryLogged = true;
        windowStartMillis = now;
        scheduleSummary(now, SUMMARY_INTERVAL_MILLIS);
    }

    private void scheduleSummary(final long now, final long delayMillis) {
        nextSummaryMillis = now + delayMillis;
        final long scheduledGeneration = ++generation;
        scheduler.schedule(() -> summarizeIfDue(scheduledGeneration, true), delayMillis);
    }

    /**
     * Called under the lock, so the lines queue in the order they were decided.
     */
    private void write(final Runnable writeLine) {
        pendingLines.add(writeLine);
        scheduler.schedule(this::writePendingLines, 0);
    }

    private void writePendingLines() {
        synchronized (outputLock) {
            for (Runnable writeLine = pendingLines.poll(); writeLine != null; writeLine = pendingLines.poll()) {
                writeLine.run();
            }
        }
    }

    private static final class Failures {
        /**
         * Failures since the previous summary; a key still failing without a new attempt is listed with zero.
         */
        private int count;
        private String lastCause;

        private void add(final String cause) {
            count++;
            lastCause = cause;
        }

        private void startWindow() {
            count = 0;
        }
    }
}
