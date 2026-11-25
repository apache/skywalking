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

package org.apache.skywalking.oap.server.admin.dsl.debugging.rest;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.PeerPage;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterCaptureTime;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterExecutionRecord;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugRecordsSummary;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.CaptureTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CapturePagingTest {

    private static final CaptureTime FIRST = CaptureTime.EARLIEST;

    @Test
    void aNodeCutShortHoldsTheOthersLaterRecordsForTheNextPage() {
        // Node A's page was cut before its record at 30; node B sent everything before its clock, 50.
        final CapturePaging.NodePage a = page(t(30), t(10), t(20));
        final CapturePaging.NodePage b = page(t(50), t(15), t(30), t(45));

        final CaptureTime next = CapturePaging.nextSince(Arrays.asList(a, b), FIRST, 1000);

        assertEquals(t(30), next, "A's records from 30 on were not sent, so the response is complete before 30 only");
        assertEquals(2, CapturePaging.kept(a, FIRST, next).size());
        assertEquals(1, CapturePaging.kept(b, FIRST, next).size(), "B's 30 and 45 come with the next page");
        assertEquals(2, CapturePaging.kept(b, next, t(50)).size());
    }

    @Test
    void aNodeWhoseClockRunsBehindBoundsThePoint() {
        final CapturePaging.NodePage ahead = page(t(100), t(90));
        final CapturePaging.NodePage behind = page(t(60));

        assertEquals(t(60), CapturePaging.nextSince(Arrays.asList(ahead, behind), FIRST, 1000),
            "a record the slow node captures next is at or after 60, so nothing before the point is missed");
    }

    @Test
    void aCappedNodeWhoseClockRunsBehindHoldsBackNone() {
        // B's clock runs 5 s behind A's. While B captures, a record it captures next may be earlier than A's, so B
        // bounds the point; once B reached its cap it captures no more, and A's records come at once.
        final CapturePaging.NodePage ahead = page(CaptureTime.LATEST, t(5010), t(5020));
        assertEquals(t(10), CapturePaging.nextSince(Arrays.asList(ahead, page(t(10))), FIRST, 1000));

        final CapturePaging.NodePage cappedBehind = page(CaptureTime.LATEST, t(5));
        final CaptureTime next = CapturePaging.nextSince(Arrays.asList(ahead, cappedBehind), FIRST, 1000);
        assertEquals(CaptureTime.LATEST, next, "every node is read through");
        assertEquals(2, CapturePaging.kept(ahead, FIRST, next).size());
        assertEquals(1, CapturePaging.kept(cappedBehind, FIRST, next).size());
    }

    @Test
    void aCaptureIsReadThroughOnlyWhenEveryNodeReachedTheLatestTime() {
        final CapturePaging.NodePage capped = page(CaptureTime.LATEST, t(10));
        // Read 1: the other node did not answer, and the serving node's clock, 40, stands in for it.
        final CaptureTime first = CapturePaging.nextSince(Arrays.asList(capped, page(t(40))), FIRST, 1000);
        assertEquals(t(40), first, "a node that did not answer keeps the point from jumping to the latest time");
        assertFalse(CapturePaging.readThrough(Arrays.asList(capped, page(t(40))), first));

        // A point already at the latest time never moves back, so it proves nothing about a node still capturing.
        final List<CapturePaging.NodePage> stillCapturing = Arrays.asList(capped, page(t(50), t(45)));
        final CaptureTime clamped = CapturePaging.nextSince(stillCapturing, CaptureTime.LATEST, 1000);
        assertEquals(CaptureTime.LATEST, clamped);
        assertFalse(CapturePaging.readThrough(stillCapturing, clamped), "a node still captures");

        final List<CapturePaging.NodePage> bothCapped = Arrays.asList(capped, page(CaptureTime.LATEST, t(20)));
        assertTrue(CapturePaging.readThrough(bothCapped, CapturePaging.nextSince(bothCapped, FIRST, 1000)));
        assertFalse(CapturePaging.readThrough(bothCapped, CapturePaging.nextSince(bothCapped, FIRST, 1)),
            "a response the page size cut holds back the rest");
        assertFalse(CapturePaging.readThrough(new ArrayList<>(), CaptureTime.LATEST), "no node holds the session");
    }

    @Test
    void aNodeThatDidNotAnswerBoundsThePointOnlyBesideANodeThatDid() {
        final CapturePaging.NodePage capped = page(CaptureTime.LATEST, t(10));
        final List<CapturePaging.NodePage> beside =
            CapturePaging.withUnanswered(Collections.singletonList(capped), true, t(40));
        assertEquals(t(40), CapturePaging.nextSince(beside, FIRST, 1000),
            "the serving node's clock stands in for the node that did not answer");
        assertFalse(CapturePaging.readThrough(beside, t(40)));

        // No node holding the session answered: the point stays where the client gave it, to read from there again.
        final List<CapturePaging.NodePage> none = CapturePaging.withUnanswered(new ArrayList<>(), true, t(40));
        assertTrue(none.isEmpty());
        assertEquals(FIRST, CapturePaging.nextSince(none, FIRST, 1000));
        assertEquals(t(20), CapturePaging.nextSince(none, t(20), 1000));

        assertEquals(1, CapturePaging.withUnanswered(Collections.singletonList(capped), false, t(40)).size());
    }

    @Test
    void aRecordAtThePointComesWithTheNextPage() {
        // A record captured in the same clock tick the node was read at.
        final CapturePaging.NodePage page = page(t(50), t(40), t(50));

        final CaptureTime next = CapturePaging.nextSince(List.of(page), FIRST, 1000);
        assertEquals(t(50), next);
        assertEquals(1, CapturePaging.kept(page, FIRST, next).size());
        assertEquals(1, CapturePaging.kept(page(t(60), t(50)), next, t(60)).size(), "never lost, never twice");
    }

    @Test
    void thePageSizeCutsBetweenCaptureTimesNeverInsideOne() {
        final CapturePaging.NodePage a = page(t(100), t(10), t(20), t(30));
        final CapturePaging.NodePage b = page(t(100), t(20), t(40));

        final CaptureTime next = CapturePaging.nextSince(Arrays.asList(a, b), FIRST, 2);

        assertEquals(t(30), next, "the two records captured at 20 stay together, past the page size");
        assertEquals(2, CapturePaging.kept(a, FIRST, next).size());
        assertEquals(1, CapturePaging.kept(b, FIRST, next).size());
    }

    @Test
    void nanosOrderRecordsWithinAMillisecond() {
        final CapturePaging.NodePage page = page(new CaptureTime(10L, 900),
            new CaptureTime(10L, 100), new CaptureTime(10L, 500), new CaptureTime(10L, 800));

        final CaptureTime next = CapturePaging.nextSince(List.of(page), FIRST, 2);
        assertEquals(new CaptureTime(10L, 800), next);
    }

    @Test
    void aPeerWithoutTheSessionTakesNoPartInThePage() {
        // Its summary carries no point, which would otherwise hold every node's records back.
        final PeerPage notLocal = new PeerPage(
            CollectDebugRecordsSummary.newBuilder().setStatus("not_local").build(), new ArrayList<>());
        assertNull(DSLDebuggingRestHandler.peerPage(notLocal));

        final PeerPage holding = new PeerPage(
            CollectDebugRecordsSummary.newBuilder().setStatus("ok").setCompleteBefore(wire(90)).build(),
            List.of(ClusterExecutionRecord.newBuilder().setCapturedAt(wire(80)).build()));
        final CapturePaging.NodePage page = DSLDebuggingRestHandler.peerPage(holding);
        assertEquals(t(90), CapturePaging.nextSince(List.of(page), FIRST, 1000));
        assertEquals(1, CapturePaging.kept(page, FIRST, t(90)).size());
    }

    @Test
    void thePointNeverMovesBack() {
        assertEquals(t(500), CapturePaging.nextSince(List.of(page(t(300))), t(500), 1000));
        assertEquals(t(500), CapturePaging.nextSince(new ArrayList<>(), t(500), 1000), "no node answered");
    }

    private static CaptureTime t(final long millis) {
        return new CaptureTime(millis, 0);
    }

    private static ClusterCaptureTime wire(final long millis) {
        return ClusterCaptureTime.newBuilder().setMillis(millis).build();
    }

    private static CapturePaging.NodePage page(final CaptureTime completeBefore, final CaptureTime... capturedAt) {
        final List<CapturePaging.Entry> entries = new ArrayList<>();
        for (final CaptureTime at : capturedAt) {
            entries.add(new CapturePaging.Entry(at, 10, new JsonObject()));
        }
        return new CapturePaging.NodePage(entries, completeBefore);
    }
}
