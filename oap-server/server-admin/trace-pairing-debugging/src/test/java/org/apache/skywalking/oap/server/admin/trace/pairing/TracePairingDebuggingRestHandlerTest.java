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

package org.apache.skywalking.oap.server.admin.trace.pairing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.admin.server.cluster.AdminClusterChannelManager;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.PairingClusterClient;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.SpanPairing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracePairingDebuggingRestHandlerTest {
    private static final String PEER = "catalog:3550";

    private SpanPairing pairing;
    private PairingCapture capture;
    private TracePairingDebuggingRestHandler handler;

    @BeforeEach
    void setUp() {
        pairing = new SpanPairing();
        capture = new PairingCapture(pairing.getDebugGate());
        handler = new TracePairingDebuggingRestHandler(capture, new PairingClusterClient(new TestNoPeers()), "n1");
    }

    @Test
    void aStartedCaptureListsEachPeerMatchedToItsServer() {
        handler.start();
        call("t1", "frontend", "s2", PEER, "catalog", "s3");
        call("t2", "checkout", "s4", PEER, "catalog", "s5");
        pairing.clientSeen(client("t3", "frontend", "s6"), "cart:7070");

        final JsonObject found = body(handler.pairings(1000));
        assertEquals("capturing", found.get("state").getAsString());
        assertEquals(3, found.getAsJsonObject("received").get("caller").getAsLong());
        assertEquals(2, found.getAsJsonObject("received").get("callee").getAsLong());
        assertEquals(2, found.get("matched").getAsLong(), "the cart half still waits");
        final JsonArray matched = found.getAsJsonArray("matchedPeers");
        assertEquals(1, matched.size(), "one row per peer");
        final JsonObject peer = matched.get(0).getAsJsonObject();
        assertEquals(PEER, peer.get("peer").getAsString());
        assertEquals("catalog", peer.get("server").getAsString());
        assertEquals(Arrays.asList("checkout", "frontend"), strings(peer.getAsJsonArray("callers")), "latest first");
        assertEquals(2, peer.get("count").getAsLong());
        assertEquals(2, peer.getAsJsonObject("outcomes").get("composed").getAsLong(), "no registrar here");
        assertEquals("n1", found.getAsJsonArray("nodes").get(0).getAsJsonObject().get("nodeId").getAsString());
    }

    @Test
    void aPeerMatchedToAnotherServerReadsAsUpdated() {
        handler.start();
        call("t1", "frontend", "s2", PEER, "catalog", "s3");
        call("t2", "frontend", "s4", PEER, "catalog-v2", "s5");

        final JsonObject peer = body(handler.pairings(1000)).getAsJsonArray("matchedPeers").get(0).getAsJsonObject();
        assertEquals("catalog-v2", peer.get("server").getAsString());
        assertEquals("catalog", peer.get("previousServer").getAsString());
    }

    @Test
    void unmatchedHalvesListByPeerAndByServerWithTheirLatestFiveTraces() {
        handler.start();
        // A later half of one key, one trace's span sent twice, replaces the waiting one, which leaves unmatched.
        for (int i = 1; i <= 7; i++) {
            pairing.clientSeen(client("t" + i, "frontend", "s2"), PEER);
            pairing.clientSeen(client("t" + i, "frontend", "s2"), PEER);
        }
        pairing.serverSeen(server("u1", "catalog", "s3", "p1"), true, "OTLP_GENERAL", "otlp-general");
        pairing.serverSeen(server("u1", "catalog", "s4", "p1"), true, "OTLP_GENERAL", "otlp-general");

        final JsonObject found = body(handler.pairings(1000));
        final JsonObject peer = found.getAsJsonArray("unmatchedPeers").get(0).getAsJsonObject();
        assertEquals(PEER, peer.get("peer").getAsString());
        assertEquals(7, peer.get("count").getAsLong());
        assertEquals(7, peer.getAsJsonObject("causes").get("replaced").getAsLong());
        assertEquals(Arrays.asList("t7", "t6", "t5", "t4", "t3"), strings(peer.getAsJsonArray("traceIds")),
            "the latest five, the latest first");
        assertEquals(Collections.singletonList("frontend"), strings(peer.getAsJsonArray("callers")));
        final JsonObject server = found.getAsJsonArray("unmatchedServers").get(0).getAsJsonObject();
        assertEquals("catalog", server.get("server").getAsString());
        assertEquals(Collections.singletonList("otlp-general"), strings(server.getAsJsonArray("rules")));
        assertEquals(7, found.getAsJsonObject("unmatched").getAsJsonObject("caller").get("replaced").getAsLong());
    }

    @Test
    void readsKeepTheCaptureRunningAndItStopsAMinuteAfterTheLatest() {
        assertEquals("off", body(handler.status()).getAsJsonObject("capture").get("state").getAsString());
        handler.start();
        assertTrue(pairing.getDebugGate().isOn());
        call("t1", "frontend", "s2", PEER, "catalog", "s3");
        final long read = System.currentTimeMillis();
        assertEquals(1, body(handler.pairings(1000)).get("matched").getAsLong());
        capture.expire(read + PairingCapture.IDLE_MILLIS / 2);
        assertTrue(pairing.getDebugGate().isOn(), "read half a minute ago");
        handler.status();
        capture.expire(read + PairingCapture.IDLE_MILLIS + 1000L);
        assertFalse(pairing.getDebugGate().isOn(), "the status read is no ping");

        assertReleased(body(handler.pairings(1000)), "the expiry releases the round");

        handler.start();
        call("t2", "frontend", "s4", PEER, "catalog", "s5");
        // Halves left unmatched on both sides: a caller and a callee half replaced by a later one of its key.
        pairing.clientSeen(client("t3", "frontend", "s6"), PEER);
        pairing.clientSeen(client("t3", "frontend", "s6"), PEER);
        pairing.serverSeen(server("t4", "catalog", "s7", "s9"), true, "OTLP_GENERAL", "otlp-general");
        pairing.serverSeen(server("t4", "catalog", "s8", "s9"), true, "OTLP_GENERAL", "otlp-general");
        final JsonObject running = body(handler.pairings(1000));
        assertEquals("capturing", running.get("state").getAsString());
        assertEquals(1, running.getAsJsonArray("matchedPeers").size());
        assertEquals(1, running.getAsJsonArray("unmatchedPeers").size());
        assertEquals(1, running.getAsJsonArray("unmatchedServers").size());
        handler.stop();
        assertFalse(pairing.getDebugGate().isOn());
        assertReleased(body(handler.pairings(1000)), "a stop releases the round");
    }

    private static void assertReleased(final JsonObject found, final String why) {
        assertEquals("off", found.get("state").getAsString(), why);
        assertEquals(0L, found.get("startTime").getAsLong(), why);
        assertEquals(0L, found.get("lastReadTime").getAsLong(), why);
        assertEquals(0L, found.getAsJsonObject("received").get("caller").getAsLong(), why);
        assertEquals(0L, found.getAsJsonObject("received").get("callee").getAsLong(), why);
        assertEquals(0L, found.get("matched").getAsLong(), why);
        assertEquals(0, found.getAsJsonObject("unmatched").size(), why);
        assertEquals("", found.get("roundId").getAsString(), why);
        assertEquals(0, found.getAsJsonArray("matchedPeers").size(), why);
        assertEquals(0, found.getAsJsonArray("unmatchedPeers").size(), why);
        assertEquals(0, found.getAsJsonArray("unmatchedServers").size(), why);
    }

    @Test
    void aRoundKeepsItsIdWhileItRunsAndAReadOrStartPastItsExpiryBeginsNoLateRenewal() {
        final JsonObject first = capture.start(1_000L, "r1", 1_000L);
        assertEquals("r1", first.get("roundId").getAsString());
        assertEquals(1_000L, first.get("startTime").getAsLong());
        final JsonObject again = capture.start(2_000L, "r2", 2_000L);
        assertEquals("r1", again.get("roundId").getAsString(), "a start while it runs keeps its round");

        // Past the idle deadline, before the sweep: the read does not renew the round, and a start begins a new one.
        capture.read(2_000L + PairingCapture.IDLE_MILLIS);
        assertEquals("off", capture.state(), "the read finds it idle past its expiry");
        assertEquals("r3", capture.start(3_000L + PairingCapture.IDLE_MILLIS, "r3", 3_000L).get("roundId").getAsString());
        capture.stop();
    }

    @Test
    void aReadIsOfTheRoundMostNodesHoldAndSumsOnlyThem() {
        final JsonObject a = summary("capturing", peerEntry("catalog", 100L), "t1");
        final JsonObject b = summary("capturing", peerEntry("catalog", 120L), "t2");
        final JsonObject apart = summary("capturing", peerEntry("payment", 130L), "t3");
        apart.addProperty("roundId", "r2");
        apart.addProperty("startTime", 0L);
        final JsonObject merged = TracePairingDebuggingRestHandler.merge(Arrays.asList(apart, a, b), 1000);
        assertEquals("r1", merged.get("roundId").getAsString(), "two nodes hold r1, one r2");
        assertEquals(4, merged.get("matched").getAsLong(), "the node in another round is not summed in");
        assertEquals(1, merged.getAsJsonArray("matchedPeers").size());
        assertEquals("catalog", merged.getAsJsonArray("matchedPeers").get(0).getAsJsonObject().get("server")
                                      .getAsString());

        // As many nodes in each round: the earliest started.
        assertEquals("r2", TracePairingDebuggingRestHandler.roundOf(Arrays.asList(a, apart)));
        assertEquals("", TracePairingDebuggingRestHandler.roundOf(Collections.emptyList()));
    }

    @Test
    void eachEntryNamesTheNodesThatHoldIt() {
        final JsonObject onA = TracePairingDebuggingRestHandler.heldBy(
            summary("capturing", peerEntry("catalog", 100L), "t1"), "oap-a:17129");
        final JsonObject onB = TracePairingDebuggingRestHandler.heldBy(
            summary("capturing", peerEntry("catalog", 200L), "t2"), "oap-b:17129");
        final JsonObject merged = TracePairingDebuggingRestHandler.merge(Arrays.asList(onA, onB), 1000);
        assertEquals(Arrays.asList("oap-b:17129", "oap-a:17129"), strings(merged.getAsJsonArray("matchedPeers")
            .get(0).getAsJsonObject().getAsJsonArray("nodes")), "the node that saw it last first");
        assertEquals(Arrays.asList("oap-b:17129", "oap-a:17129"), strings(merged.getAsJsonArray("unmatchedPeers")
            .get(0).getAsJsonObject().getAsJsonArray("nodes")));

        final JsonObject alone = body(handler.pairings(1000)).getAsJsonArray("nodes").get(0).getAsJsonObject();
        assertEquals("n1", alone.get("nodeId").getAsString());
        assertFalse(alone.has("self"), "no node is set apart as the one that took the request");
    }

    @Test
    void aStoppedOrExpiredNodeHoldsNothingAndARestartBeginsEmpty() {
        final JsonObject started = body(handler.start()).getAsJsonArray("nodes").get(0).getAsJsonObject();
        assertEquals("n1", started.get("nodeId").getAsString(), "the acknowledgement names each node");
        final String first = started.getAsJsonObject("capture").get("roundId").getAsString();
        assertFalse(first.isEmpty());
        call("t1", "frontend", "s2", PEER, "catalog", "s3");
        pairing.clientSeen(client("t2", "frontend", "s4"), PEER);
        pairing.clientSeen(client("t2", "frontend", "s4"), PEER);
        assertEquals(1, capture.summary().getAsJsonArray("unmatchedPeers").size());

        final JsonObject stopped = body(handler.stop()).getAsJsonArray("nodes").get(0).getAsJsonObject();
        assertEquals("off", stopped.getAsJsonObject("capture").get("state").getAsString());
        assertReleased(capture.summary(), "the node itself holds nothing once stopped");

        handler.start();
        final JsonObject restarted = capture.summary();
        assertNodeEmpty(restarted, "a restart begins an empty round");
        assertFalse(first.equals(restarted.get("roundId").getAsString()), "under a new id");
        call("t3", "frontend", "s5", PEER, "catalog", "s6");
        pairing.clientSeen(client("t4", "frontend", "s7"), PEER);
        pairing.clientSeen(client("t4", "frontend", "s7"), PEER);
        assertEquals(1L, capture.summary().get("matched").getAsLong());
        capture.expire(System.currentTimeMillis() + PairingCapture.IDLE_MILLIS + 1000L);
        assertReleased(capture.summary(), "nor once expired");
    }

    private static void assertNodeEmpty(final JsonObject summary, final String why) {
        assertEquals(0L, summary.get("matched").getAsLong(), why);
        assertEquals(0L, summary.getAsJsonObject("received").get("caller").getAsLong(), why);
        assertEquals(0, summary.getAsJsonObject("unmatched").size(), why);
        assertEquals(0, summary.getAsJsonArray("matchedPeers").size(), why);
        assertEquals(0, summary.getAsJsonArray("unmatchedPeers").size(), why);
        assertEquals(0, summary.getAsJsonArray("unmatchedServers").size(), why);
    }

    @Test
    void aListLimitOutOfItsBoundsIsRefused() {
        assertEquals(400, handler.pairings(0).aggregate().join().status().code());
        assertEquals(400, handler.pairings(PairingCapture.MAX_ENTRIES + 1).aggregate().join().status().code());
    }

    @Test
    void nodesMergeOneEntryOfAKeyIntoOne() {
        final JsonObject a = summary("capturing", peerEntry("catalog", 100L), "t1", "t2");
        final JsonObject b = summary("capturing", peerEntry("catalog-v2", 200L), "t3");
        // A node that is off holds nothing.
        final JsonObject off = JsonParser.parseString("{\"state\":\"off\",\"roundId\":\"\",\"startTime\":0,\"lastReadTime\":0,"
            + "\"idleMillis\":60000,\"received\":{\"caller\":0,\"callee\":0},\"matched\":0,\"unmatched\":{},"
            + "\"matchedPeers\":[],\"unmatchedPeers\":[],\"unmatchedServers\":[]}").getAsJsonObject();
        final JsonObject merged = TracePairingDebuggingRestHandler.merge(Arrays.asList(a, b, off), 1000);
        assertEquals("capturing", merged.get("state").getAsString());
        assertEquals(4, merged.get("matched").getAsLong());
        final JsonObject peer = merged.getAsJsonArray("matchedPeers").get(0).getAsJsonObject();
        assertEquals(4, peer.get("count").getAsLong());
        assertEquals("catalog-v2", peer.get("server").getAsString(), "the node that saw it last");
        assertEquals("catalog", peer.get("previousServer").getAsString());
        assertEquals(100L, peer.get("firstTime").getAsLong());
        final JsonObject lost = merged.getAsJsonArray("unmatchedPeers").get(0).getAsJsonObject();
        assertEquals(Arrays.asList("t3", "t1", "t2"), strings(lost.getAsJsonArray("traceIds")));
    }

    @Test
    void aMoveSeenOnAnOlderNodeAndTheLatestOfSeveralServersSurviveTheMerge() {
        // The newer node only saw the current server; the older one saw the move to it.
        final JsonObject moved = peerEntry("catalog-v2", 200L);
        moved.addProperty("previousServer", "catalog-v1");
        moved.addProperty("previousTime", 140L);
        moved.addProperty("updatedTime", 150L);
        moved.addProperty("address", "frontend->catalog:3550");
        final JsonObject merged = TracePairingDebuggingRestHandler.merge(Arrays.asList(
            summary("capturing", moved), summary("capturing", peerEntry("catalog-v2", 205L))), 1000);
        final JsonObject peer = merged.getAsJsonArray("matchedPeers").get(0).getAsJsonObject();
        assertEquals("catalog-v2", peer.get("server").getAsString());
        assertEquals("catalog-v1", peer.get("previousServer").getAsString());
        assertEquals(140L, peer.get("previousTime").getAsLong());
        assertEquals(150L, peer.get("updatedTime").getAsLong());
        assertFalse(peer.has("address"), "the latest match, on the newer node, composed none");

        // Node A matched v1 at 100 and v3 at 300; node B matched v2 at 200. The peer moved v1, v2, v3.
        final JsonObject onA = peerEntry("v3", 300L);
        onA.addProperty("previousServer", "v1");
        onA.addProperty("previousTime", 100L);
        onA.addProperty("updatedTime", 300L);
        final JsonObject interleaved = TracePairingDebuggingRestHandler.merge(Arrays.asList(
            summary("capturing", onA), summary("capturing", peerEntry("v2", 200L))), 1000)
            .getAsJsonArray("matchedPeers").get(0).getAsJsonObject();
        assertEquals("v3", interleaved.get("server").getAsString());
        assertEquals("v2", interleaved.get("previousServer").getAsString(), "matched after A's last match of v1");
        assertEquals(200L, interleaved.get("previousTime").getAsLong());

        // Three nodes, three servers: the one the latest moved from is the most recently seen other one.
        final JsonObject three = TracePairingDebuggingRestHandler.merge(Arrays.asList(
            summary("capturing", peerEntry("s3", 300L)), summary("capturing", peerEntry("s1", 100L)),
            summary("capturing", peerEntry("s2", 200L))), 1000);
        final JsonObject latest = three.getAsJsonArray("matchedPeers").get(0).getAsJsonObject();
        assertEquals("s3", latest.get("server").getAsString());
        assertEquals("s2", latest.get("previousServer").getAsString());
        assertFalse(latest.has("updatedTime"), "no node saw the move: when it moved is not known");
    }

    private static JsonObject summary(final String state, final JsonObject peer, final String... traceIds) {
        final JsonObject summary = JsonParser.parseString("{\"roundId\":\"r1\",\"startTime\":1,\"lastReadTime\":2,"
            + "\"idleMillis\":60000,\"received\":{\"caller\":2,\"callee\":2},\"matched\":2,"
            + "\"unmatched\":{},\"unmatchedServers\":[]}").getAsJsonObject();
        summary.addProperty("state", state);
        final JsonArray matched = new JsonArray();
        matched.add(peer);
        summary.add("matchedPeers", matched);
        final JsonObject lost = new JsonObject();
        lost.addProperty("peer", PEER);
        lost.add("callers", new JsonArray());
        lost.addProperty("count", 1L);
        lost.add("causes", new JsonObject());
        lost.add("firstTime", peer.get("firstTime"));
        lost.add("lastTime", peer.get("lastTime"));
        final JsonArray ids = new JsonArray();
        Arrays.stream(traceIds).forEach(ids::add);
        lost.add("traceIds", ids);
        final JsonArray unmatched = new JsonArray();
        unmatched.add(lost);
        summary.add("unmatchedPeers", unmatched);
        return summary;
    }

    private static JsonObject peerEntry(final String server, final long time) {
        final JsonObject peer = new JsonObject();
        peer.addProperty("peer", PEER);
        peer.addProperty("server", server);
        peer.add("callers", new JsonArray());
        peer.addProperty("count", 2L);
        peer.add("outcomes", new JsonObject());
        peer.addProperty("firstTime", time);
        peer.addProperty("lastTime", time);
        return peer;
    }

    private void call(final String traceId, final String caller, final String callerSpan, final String peer,
                      final String callee, final String calleeSpan) {
        pairing.clientSeen(client(traceId, caller, callerSpan), peer);
        pairing.serverSeen(server(traceId, callee, calleeSpan, callerSpan), true, "OTLP_GENERAL", "otlp-general");
        // Calls a millisecond apart, so the latest-first order is the order they were made in.
        try {
            Thread.sleep(2L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<String> strings(final JsonArray array) {
        final List<String> out = new ArrayList<>();
        array.forEach(e -> out.add(e.getAsString()));
        return out;
    }

    private static JsonObject body(final HttpResponse response) {
        final AggregatedHttpResponse aggregated = response.aggregate().join();
        assertEquals(200, aggregated.status().code(), aggregated.contentUtf8());
        return JsonParser.parseString(aggregated.contentUtf8()).getAsJsonObject();
    }

    private static SpanMetadata client(final String traceId, final String service, final String spanId) {
        return SpanMetadata.builder().service(service).kind(SpanMetadata.KIND_CLIENT).traceId(traceId).spanId(spanId)
                           .parentSpanId("s1").timestamp(1_700_000_000_500L).build();
    }

    private static SpanMetadata server(final String traceId, final String service, final String spanId,
                                       final String parent) {
        return SpanMetadata.builder().service(service).kind(SpanMetadata.KIND_SERVER).traceId(traceId).spanId(spanId)
                           .parentSpanId(parent).timestamp(1_700_000_000_600L).build();
    }

    /**
     * A single-node cluster.
     */
    private static final class TestNoPeers implements AdminClusterChannelManager {
        @Override
        public List<Peer> getPeers() {
            return Collections.emptyList();
        }

        @Override
        public long getInternalCommunicationTimeoutMs() {
            return 1000L;
        }

        @Override
        public int getMaxMessageSize() {
            return 0;
        }
    }
}
