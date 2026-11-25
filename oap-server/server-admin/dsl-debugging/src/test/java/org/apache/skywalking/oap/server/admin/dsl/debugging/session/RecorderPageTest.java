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

package org.apache.skywalking.oap.server.admin.dsl.debugging.session;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.dsl.Catalog;
import org.apache.skywalking.oap.server.core.dsl.debug.GateHolder;
import org.apache.skywalking.oap.server.core.dsl.debug.RuleKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecorderPageTest {

    @Test
    void recordsAreListedInCaptureOrder() {
        final TestPagedRecorder recorder = new TestPagedRecorder();
        for (int i = 0; i < 50; i++) {
            recorder.publish("{}");
        }
        final List<ExecutionRecord> records = recorder.snapshotRecords();
        for (int i = 1; i < records.size(); i++) {
            assertTrue(records.get(i).getCapturedAt().compareTo(records.get(i - 1).getCapturedAt()) >= 0);
        }
    }

    @Test
    void pagesFromEachPointCoverTheCaptureOnceAndInOrder() {
        // Records published back to back may share a capture time, on a clock that ticks in microseconds; a page
        // never splits them, so walking the pages still returns every record once.
        final TestPagedRecorder recorder = new TestPagedRecorder();
        for (int i = 0; i < 40; i++) {
            recorder.publish("{\"n\":" + i + "}");
        }
        final List<ExecutionRecord> read = new ArrayList<>();
        CaptureTime since = CaptureTime.EARLIEST;
        // A page the bounds cut ends before the first record it left out, where the next one starts; the last page
        // runs up to the node's clock and holds what is left.
        for (int round = 0; round < 100 && read.size() < 40; round++) {
            final CapturePage page = recorder.page(since, 3, Long.MAX_VALUE, ExecutionRecord::byteSize);
            for (final ExecutionRecord r : page.getRecords()) {
                assertTrue(r.getCapturedAt().compareTo(since) >= 0, "a page starts at its point");
                assertTrue(r.getCapturedAt().compareTo(page.getCompleteBefore()) <= 0);
            }
            read.addAll(page.getRecords());
            since = page.getCompleteBefore();
        }
        assertEquals(recorder.snapshotRecords(), read, "every record once, in capture order");
    }

    @Test
    void aClockSteppingBackLeavesThePagesInTimeOrder() {
        // The wall clock stepped back between the first and the second record, so the list is not in time order.
        final TestPagedRecorder recorder = new TestPagedRecorder();
        recorder.publish("{\"n\":0}");
        recorder.publish("{\"n\":1}");
        recorder.publish("{\"n\":2}");
        final List<ExecutionRecord> listed = recorder.snapshotRecords();
        listed.get(0).capture(new CaptureTime(1000L, 0));
        listed.get(1).capture(new CaptureTime(950L, 0));
        listed.get(2).capture(new CaptureTime(1100L, 0));

        final CapturePage first = recorder.page(CaptureTime.EARLIEST, 1, Long.MAX_VALUE, ExecutionRecord::byteSize);
        assertEquals(List.of(listed.get(1)), first.getRecords());
        assertEquals(new CaptureTime(1000L, 0), first.getCompleteBefore(), "the next page starts at the next time");
        final CapturePage second = recorder.page(first.getCompleteBefore(), 1, Long.MAX_VALUE,
            ExecutionRecord::byteSize);
        assertEquals(List.of(listed.get(0)), second.getRecords());
        final CapturePage third = recorder.page(second.getCompleteBefore(), 1, Long.MAX_VALUE,
            ExecutionRecord::byteSize);
        assertEquals(List.of(listed.get(2)), third.getRecords());
    }

    @Test
    void aRecordCapturedAfterAReadIsAtOrAfterItsPoint() {
        final TestPagedRecorder recorder = new TestPagedRecorder();
        recorder.publish("{\"n\":0}");
        final CapturePage first = recorder.page(CaptureTime.EARLIEST, 100, Long.MAX_VALUE, ExecutionRecord::byteSize);
        recorder.publish("{\"n\":1}");

        final ExecutionRecord later = recorder.snapshotRecords().get(1);
        assertTrue(later.getCapturedAt().compareTo(first.getCompleteBefore()) >= 0,
            "the next page, from this point, holds it");
    }

    @Test
    void theFirstRecordIsTakenWhateverItsSize() {
        final TestPagedRecorder recorder = new TestPagedRecorder();
        recorder.publish("{\"big\":\"" + "x".repeat(1000) + "\"}");
        recorder.publish("{}");

        final CapturePage page = recorder.page(CaptureTime.EARLIEST, 100, 10, ExecutionRecord::byteSize);
        assertFalse(page.getRecords().isEmpty(), "a page always moves on");
    }

    @Test
    void aPageReadsTheCapAndTheSizeWithItsRecords() {
        final TestPagedRecorder recorder = new TestPagedRecorder();
        for (int i = 0; i < 100; i++) {
            recorder.publish("{}");
        }
        final CapturePage page = recorder.page(CaptureTime.EARLIEST, 1000, Long.MAX_VALUE, ExecutionRecord::byteSize);

        assertTrue(page.isCaptured(), "a page that says captured holds every record before its point");
        assertEquals(100, page.getRecords().size());
        assertEquals(recorder.totalBytes(), page.getTotalBytes());
    }

    @Test
    void aCappedRecorderIsCompleteBeforeTheLatestTimeOnceItsPageHoldsTheRest() {
        final TestPagedRecorder recorder = new TestPagedRecorder();
        for (int i = 0; i < 100; i++) {
            recorder.publish("{}");
        }
        final List<ExecutionRecord> listed = recorder.snapshotRecords();
        for (int i = 0; i < listed.size(); i++) {
            listed.get(i).capture(new CaptureTime(1000L + i, 0));
        }

        final CapturePage whole = recorder.page(CaptureTime.EARLIEST, 1000, Long.MAX_VALUE, ExecutionRecord::byteSize);
        assertEquals(CaptureTime.LATEST, whole.getCompleteBefore(), "it captures no more, so its clock bounds nothing");
        final CapturePage cut = recorder.page(CaptureTime.EARLIEST, 10, Long.MAX_VALUE, ExecutionRecord::byteSize);
        assertEquals(new CaptureTime(1010L, 0), cut.getCompleteBefore(), "a page cut short ends where it cut");
    }

    private static final class TestPagedRecorder extends AbstractDebugRecorder {
        TestPagedRecorder() {
            super("session", new RuleKey(Catalog.TAL, "file", "rule"), new GateHolder(""),
                new SessionLimits(100, 60_000L));
        }

        void publish(final String body) {
            publishRecord(newRecord(System.currentTimeMillis(), body));
        }
    }
}
