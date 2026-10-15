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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FailureSummaryLoggerTest {
    private final AtomicLong now = new AtomicLong(TimeUnit.HOURS.toMillis(1));
    private final List<String> failureLines = new ArrayList<>();
    private final List<String> recoveryLines = new ArrayList<>();
    private final List<Long> scheduledDelays = new ArrayList<>();
    private final FailureSummaryLogger logger = new FailureSummaryLogger(
        "Kubernetes API reads", "lookups use the data last read, if any",
        failureLines::add, recoveryLines::add, now::get, (task, delayMillis) -> {
            if (delayMillis == 0) {
                task.run();
            } else {
                scheduledDelays.add(delayMillis);
            }
        });

    @Test
    public void firstSummaryOneMinuteAfterTheFirstFailureThenEveryFiveMinutes() {
        logger.recordFailure("pods", "code=403");
        logger.recordFailure("apiserver", "type=ConnectException");
        assertEquals(List.of(TimeUnit.MINUTES.toMillis(1)), scheduledDelays);
        advanceSeconds(59);
        logger.recordFailure("apiserver", "type=SocketTimeoutException");
        logger.logIfDue();
        assertTrue(failureLines.isEmpty());

        advanceSeconds(1);
        logger.logIfDue();
        assertEquals(List.of(
            "Kubernetes API reads failed for 2 targets in the last 1 minute, lookups use the data last read, if any: "
                + "apiserver type=SocketTimeoutException count=2, pods code=403 count=1"), failureLines);
        assertEquals(List.of(TimeUnit.MINUTES.toMillis(1), TimeUnit.MINUTES.toMillis(5)), scheduledDelays);

        advanceSeconds(60);
        logger.recordFailure("pods", "code=403");
        advanceSeconds(239);
        logger.logIfDue();
        assertEquals(1, failureLines.size());

        // A key stays listed until it succeeds, with the failures since the previous summary.
        advanceSeconds(1);
        logger.logIfDue();
        assertEquals("Kubernetes API reads failed for 2 targets in the last 5 minutes, "
                         + "lookups use the data last read, if any: pods code=403 count=1, "
                         + "apiserver type=SocketTimeoutException count=0", failureLines.get(1));
    }

    @Test
    public void scheduledSummaryThatRunsEarlyWaitsForItsDeadline() {
        final List<Runnable> tasks = new ArrayList<>();
        final FailureSummaryLogger scheduled = new FailureSummaryLogger(
            "Kubernetes API reads", "lookups use the data last read, if any",
            failureLines::add, recoveryLines::add, now::get, (task, delayMillis) -> {
                if (delayMillis == 0) {
                    task.run();
                } else {
                    tasks.add(task);
                    scheduledDelays.add(delayMillis);
                }
            });
        scheduled.recordFailure("pods", "code=403");

        advanceSeconds(50);
        tasks.get(0).run();
        assertTrue(failureLines.isEmpty());
        assertEquals(List.of(TimeUnit.MINUTES.toMillis(1), TimeUnit.SECONDS.toMillis(10)), scheduledDelays);

        advanceSeconds(10);
        tasks.get(1).run();
        assertEquals(1, failureLines.size());
        assertEquals(TimeUnit.MINUTES.toMillis(5), scheduledDelays.get(2));
    }

    @Test
    public void scheduledSummaryOfARecoveredEpisodeDoesNothing() {
        final List<Runnable> tasks = new ArrayList<>();
        final FailureSummaryLogger scheduled = new FailureSummaryLogger(
            "Kubernetes API reads", "lookups use the data last read, if any",
            failureLines::add, recoveryLines::add, now::get, (task, delayMillis) -> {
                if (delayMillis == 0) {
                    task.run();
                } else {
                    tasks.add(task);
                }
            });
        scheduled.recordFailure("pods", "code=403");
        advanceSeconds(30);
        scheduled.recordSuccess("pods");
        scheduled.recordFailure("pods", "code=403");

        // The first episode's summary runs when the second episode's is not due yet.
        advanceSeconds(30);
        tasks.get(0).run();
        assertTrue(failureLines.isEmpty());
        assertEquals(2, tasks.size());

        advanceSeconds(30);
        tasks.get(1).run();
        assertEquals(1, failureLines.size());
    }

    @Test
    public void writesLinesInTheOrderTheyWereDecided() {
        final List<String> lines = new ArrayList<>();
        final List<Runnable> tasks = new ArrayList<>();
        final FailureSummaryLogger ordered = new FailureSummaryLogger(
            "Kubernetes API reads", "lookups use the data last read, if any",
            line -> lines.add("ERROR " + line), line -> lines.add("INFO " + line), now::get,
            (task, delayMillis) -> tasks.add(task));
        ordered.recordFailure("pods", "code=403");
        advanceSeconds(60);
        tasks.get(0).run();
        ordered.recordSuccess("pods");

        // The recovery's write runs before the summary's, as tasks may on a virtual thread each: it writes both.
        tasks.get(tasks.size() - 1).run();
        new ArrayList<>(tasks).forEach(Runnable::run);
        assertEquals(List.of(
            "ERROR Kubernetes API reads failed for 1 target in the last 1 minute, "
                + "lookups use the data last read, if any: pods code=403 count=1",
            "INFO Kubernetes API reads recovered."), lines);
    }

    @Test
    public void listsAtMostTenTargetsByFailureCount() {
        for (int i = 0; i < 12; i++) {
            for (int attempt = 0; attempt <= i; attempt++) {
                logger.recordFailure("host-" + i, "type=DnsTimeoutException");
            }
        }
        advanceSeconds(60);
        logger.logIfDue();

        final String line = failureLines.get(0);
        assertTrue(line.startsWith("Kubernetes API reads failed for 12 targets"), line);
        assertTrue(line.contains(": host-11 type=DnsTimeoutException count=12, host-10"), line);
        assertTrue(line.endsWith("host-2 type=DnsTimeoutException count=3 ... and 2 more"), line);
    }

    @Test
    public void shortFailureLogsNothing() {
        logger.recordFailure("pods", "code=403");
        advanceSeconds(30);
        logger.recordSuccess("pods");
        advanceSeconds(30);
        logger.logIfDue();

        assertTrue(failureLines.isEmpty());
        assertTrue(recoveryLines.isEmpty());
    }

    @Test
    public void logsRecoveryOnceEveryFailingTargetSucceedsAfterASummary() {
        logger.recordFailure("pods", "code=403");
        logger.recordFailure("services", "code=403");
        advanceSeconds(60);
        logger.logIfDue();
        assertEquals(1, failureLines.size());

        logger.recordSuccess("pods");
        assertTrue(recoveryLines.isEmpty());
        logger.recordSuccess("services");
        assertEquals(List.of("Kubernetes API reads recovered."), recoveryLines);

        // The summary pending for the recovered episode does nothing; a new failure opens a new one-minute window.
        advanceSeconds(300);
        logger.logIfDue();
        assertEquals(1, failureLines.size());
        logger.recordFailure("pods", "code=403");
        advanceSeconds(30);
        logger.logIfDue();
        assertEquals(1, failureLines.size());
        advanceSeconds(30);
        logger.logIfDue();
        assertEquals(2, failureLines.size());
    }

    private void advanceSeconds(final long seconds) {
        now.addAndGet(TimeUnit.SECONDS.toMillis(seconds));
    }
}
