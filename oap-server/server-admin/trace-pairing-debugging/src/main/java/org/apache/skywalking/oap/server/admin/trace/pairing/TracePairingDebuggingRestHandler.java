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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.Blocking;
import com.linecorp.armeria.server.annotation.Default;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.Post;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.PairingClusterClient;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.PeerOutcome;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.CaptureAck;
import org.apache.skywalking.oap.server.admin.trace.pairing.cluster.v1.CollectPairingsResponse;

/**
 * The REST surface of the pairing debugging. Any node can take a request: a call's halves meet on the node that owns
 * its trace, so it acts on its own capture and on every peer's, and merges what they found. There is one capture per
 * node, shared by every reader; reading what it found keeps it running.
 */
@Blocking
public class TracePairingDebuggingRestHandler {
    static final int DEFAULT_ENTRIES = PairingCapture.MAX_ENTRIES;
    private static final Gson GSON = new Gson();

    private final PairingCapture capture;
    private final PairingClusterClient cluster;
    private final String selfNodeId;

    public TracePairingDebuggingRestHandler(final PairingCapture capture, final PairingClusterClient cluster,
                                            final String selfNodeId) {
        this.capture = capture;
        this.cluster = cluster;
        this.selfNodeId = selfNodeId;
    }

    /**
     * This node's capture, without reading what it found, so it keeps nothing running: a cheap probe that this OAP
     * serves the pairing debugging.
     */
    @Get("/trace-pairing-debugging/status")
    public HttpResponse status() {
        capture.expire(System.currentTimeMillis());
        final JsonObject body = new JsonObject();
        body.addProperty("module", TracePairingDebuggingModule.NAME);
        body.addProperty("nodeId", selfNodeId);
        body.add("capture", capture.status());
        return json(HttpStatus.OK, body);
    }

    /**
     * Turn the capture on on every node, as a new round where it was off. This node's round, a new one or the one it
     * runs, goes to every other node, so a node a start reaches again joins the round the others hold.
     */
    @Post("/trace-pairing-debugging/start")
    public HttpResponse start() {
        final long now = System.currentTimeMillis();
        final JsonObject round = capture.start(now, UUID.randomUUID().toString(), now);
        return acks(round, cluster.start(round.get("roundId").getAsString(), round.get("startTime").getAsLong()));
    }

    /**
     * Turn the capture off on every node, releasing what it found.
     */
    @Post("/trace-pairing-debugging/stop")
    public HttpResponse stop() {
        return acks(capture.stop(), cluster.stop());
    }

    /**
     * What the capture found, summed over the nodes: its counts, the peers matched to a server, and the peers and the
     * servers whose halves went unmatched, each list the most recently seen first. A read keeps the capture running.
     */
    @Get("/trace-pairing-debugging/pairings")
    public HttpResponse pairings(@Param("limit") @Default("1000") final int limit) {
        if (limit < 1 || limit > PairingCapture.MAX_ENTRIES) {
            return error(HttpStatus.BAD_REQUEST, "invalid_param",
                "limit is from 1 to " + PairingCapture.MAX_ENTRIES + ", got " + limit);
        }
        capture.read(System.currentTimeMillis());
        final List<JsonObject> summaries = new ArrayList<>();
        final JsonArray nodes = new JsonArray();
        final String address = cluster.selfAddress();
        final JsonObject local = capture.summary();
        summaries.add(heldBy(local, address == null ? selfNodeId : address));
        nodes.add(slice(address, selfNodeId, local));
        for (final PeerOutcome<CollectPairingsResponse> peer : cluster.pairings()) {
            if (!peer.isOk()) {
                nodes.add(unreachable(peer));
                continue;
            }
            final JsonObject summary = JsonParser.parseString(peer.getAck().getSummary()).getAsJsonObject();
            summaries.add(heldBy(summary, peer.getPeer()));
            nodes.add(slice(peer.getPeer(), peer.getAck().getNodeId(), summary));
        }
        final JsonObject response = merge(summaries, limit);
        response.add("nodes", nodes);
        return json(HttpStatus.OK, response);
    }

    /**
     * @param state this node's capture as its start or stop left it
     */
    private HttpResponse acks(final JsonObject state, final List<PeerOutcome<CaptureAck>> peers) {
        final JsonArray nodes = new JsonArray();
        final JsonObject local = node(cluster.selfAddress(), selfNodeId, "ok");
        local.add("capture", state);
        nodes.add(local);
        for (final PeerOutcome<CaptureAck> peer : peers) {
            if (!peer.isOk()) {
                nodes.add(unreachable(peer));
                continue;
            }
            final JsonObject node = node(peer.getPeer(), peer.getAck().getNodeId(), "ok");
            node.add("capture", JsonParser.parseString(peer.getAck().getState()));
            nodes.add(node);
        }
        final JsonObject response = new JsonObject();
        response.add("nodes", nodes);
        return json(HttpStatus.OK, response);
    }

    /**
     * Name the OAP node that holds each entry of a summary: by its admin address, as the nodes name each other.
     */
    static JsonObject heldBy(final JsonObject summary, final String node) {
        for (final String list : new String[] {"matchedPeers", "unmatchedPeers", "unmatchedServers"}) {
            for (final JsonElement entry : summary.getAsJsonArray(list)) {
                final JsonArray nodes = new JsonArray();
                nodes.add(node);
                entry.getAsJsonObject().add("nodes", nodes);
            }
        }
        return summary;
    }

    /**
     * The round a read is of: the one most capturing nodes hold, the earliest started of those as many hold; empty
     * when no node captures. A node in another round, as one restarted and started again apart, is not summed in.
     */
    static String roundOf(final List<JsonObject> summaries) {
        final Map<String, Integer> nodes = new LinkedHashMap<>();
        final Map<String, Long> starts = new LinkedHashMap<>();
        for (final JsonObject summary : summaries) {
            if ("capturing".equals(summary.get("state").getAsString())) {
                final String id = summary.get("roundId").getAsString();
                nodes.merge(id, 1, Integer::sum);
                starts.merge(id, summary.get("startTime").getAsLong(), Math::min);
            }
        }
        return nodes.keySet().stream()
                    .min(Comparator.comparing((String id) -> -nodes.get(id))
                                   .thenComparing(starts::get)
                                   .thenComparing(Comparator.naturalOrder()))
                    .orElse("");
    }

    /**
     * Sum the counts of the nodes in the read's round, and merge an entry several of them hold into one.
     */
    static JsonObject merge(final List<JsonObject> summaries, final int limit) {
        final JsonObject merged = new JsonObject();
        final String roundId = roundOf(summaries);
        long startTime = 0L;
        long lastReadTime = 0L;
        long matched = 0L;
        final JsonObject received = new JsonObject();
        received.addProperty("caller", 0L);
        received.addProperty("callee", 0L);
        final JsonObject unmatched = new JsonObject();
        final Map<String, JsonObject> matchedPeers = new LinkedHashMap<>();
        final Map<String, JsonObject> unmatchedPeers = new LinkedHashMap<>();
        final Map<String, JsonObject> unmatchedServers = new LinkedHashMap<>();
        for (final JsonObject summary : summaries) {
            merged.add("idleMillis", summary.get("idleMillis"));
            if (roundId.isEmpty() || !"capturing".equals(summary.get("state").getAsString())
                || !roundId.equals(summary.get("roundId").getAsString())) {
                continue;
            }
            startTime = earliest(startTime, summary.get("startTime").getAsLong());
            lastReadTime = Math.max(lastReadTime, summary.get("lastReadTime").getAsLong());
            add(received, summary.getAsJsonObject("received"));
            summary.getAsJsonObject("unmatched").entrySet().forEach(role -> add(
                child(unmatched, role.getKey()), role.getValue().getAsJsonObject()));
            matched += summary.get("matched").getAsLong();
            mergeEntries(matchedPeers, summary.getAsJsonArray("matchedPeers"), "peer", "callers", null);
            mergeEntries(unmatchedPeers, summary.getAsJsonArray("unmatchedPeers"), "peer", "callers", "traceIds");
            mergeEntries(unmatchedServers, summary.getAsJsonArray("unmatchedServers"), "server", "rules", "traceIds");
        }
        merged.addProperty("state", roundId.isEmpty() ? "off" : "capturing");
        merged.addProperty("roundId", roundId);
        merged.addProperty("startTime", startTime);
        merged.addProperty("lastReadTime", lastReadTime);
        merged.add("received", received);
        merged.addProperty("matched", matched);
        merged.add("unmatched", unmatched);
        merged.add("matchedPeers", latestFirst(matchedPeers, limit));
        merged.add("unmatchedPeers", latestFirst(unmatchedPeers, limit));
        merged.add("unmatchedServers", latestFirst(unmatchedServers, limit));
        return merged;
    }

    /**
     * An entry of one key on several nodes: counts summed, the earliest first time and the latest last time, the
     * names and trace ids of the latest node first, every node that holds it, and the rest of the fields from the
     * node that saw it last.
     */
    private static void mergeEntries(final Map<String, JsonObject> into, final JsonArray entries, final String key,
                                     final String names, final String traceIds) {
        for (final JsonElement element : entries) {
            final JsonObject entry = element.getAsJsonObject();
            final JsonObject held = into.get(entry.get(key).getAsString());
            if (held == null) {
                into.put(entry.get(key).getAsString(), entry.deepCopy());
                continue;
            }
            final boolean newer = entry.get("lastTime").getAsLong() > held.get("lastTime").getAsLong();
            final JsonObject latest = newer ? entry.deepCopy() : held;
            final JsonObject other = newer ? held : entry;
            latest.addProperty("count", held.get("count").getAsLong() + entry.get("count").getAsLong());
            latest.addProperty("firstTime", earliest(held.get("firstTime").getAsLong(),
                entry.get("firstTime").getAsLong()));
            for (final String counts : new String[] {"outcomes", "causes"}) {
                if (latest.has(counts) && other.has(counts)) {
                    final JsonObject sum = held.getAsJsonObject(counts).deepCopy();
                    add(sum, entry.getAsJsonObject(counts));
                    latest.add(counts, sum);
                }
            }
            if (latest.has("server")) {
                mergeMove(latest, other);
            }
            latest.add(names, union(latest.getAsJsonArray(names), other.getAsJsonArray(names)));
            if (latest.has("nodes") && other.has("nodes")) {
                final Set<String> holders = new LinkedHashSet<>();
                latest.getAsJsonArray("nodes").forEach(node -> holders.add(node.getAsString()));
                other.getAsJsonArray("nodes").forEach(node -> holders.add(node.getAsString()));
                final JsonArray merged = new JsonArray();
                holders.forEach(merged::add);
                latest.add("nodes", merged);
            }
            if (traceIds != null) {
                latest.add(traceIds, union(latest.getAsJsonArray(traceIds), other.getAsJsonArray(traceIds)));
            }
            into.put(entry.get(key).getAsString(), latest);
        }
    }

    /**
     * The server a matched peer moved from: of the servers other than the latest one, the one matched most recently,
     * as another node's server (at its last match) or as either node's own previous server (at its last match before
     * the move). {@code updatedTime} is when the peer moved off it, as the node that saw the move tells; moved off
     * another node's server, it is not known, and left out.
     */
    private static void mergeMove(final JsonObject latest, final JsonObject other) {
        final String server = latest.get("server").getAsString();
        final List<PastServer> candidates = new ArrayList<>(3);
        for (final JsonObject node : new JsonObject[] {latest, other}) {
            if (node.has("previousServer")) {
                candidates.add(new PastServer(node.get("previousServer").getAsString(),
                    node.get("previousTime").getAsLong(),
                    node.has("updatedTime") ? node.get("updatedTime").getAsLong() : 0L));
            }
        }
        // Another node's server: when the peer moved off it is not known, only that it was after its last match there.
        candidates.add(new PastServer(other.get("server").getAsString(), other.get("lastTime").getAsLong(), 0L));
        PastServer moved = null;
        for (final PastServer candidate : candidates) {
            if (!candidate.server.equals(server) && (moved == null || candidate.lastMatched > moved.lastMatched)) {
                moved = candidate;
            }
        }
        if (moved != null) {
            latest.addProperty("previousServer", moved.server);
            latest.addProperty("previousTime", moved.lastMatched);
            if (moved.movedOff > 0) {
                latest.addProperty("updatedTime", moved.movedOff);
            } else {
                latest.remove("updatedTime");
            }
        }
    }

    /**
     * A server a peer was matched to before its latest one: when it was matched last, and when the peer moved off it,
     * 0 when not known.
     */
    @RequiredArgsConstructor
    private static final class PastServer {
        private final String server;
        private final long lastMatched;
        private final long movedOff;
    }

    private static JsonArray union(final JsonArray first, final JsonArray second) {
        final Set<String> names = new LinkedHashSet<>();
        first.forEach(name -> names.add(name.getAsString()));
        second.forEach(name -> names.add(name.getAsString()));
        final JsonArray json = new JsonArray();
        names.stream().limit(PairingCapture.NAMES_KEPT).forEach(json::add);
        return json;
    }

    private static JsonArray latestFirst(final Map<String, JsonObject> entries, final int limit) {
        final JsonArray list = new JsonArray();
        entries.values().stream()
               .sorted(Comparator.comparingLong((JsonObject entry) -> entry.get("lastTime").getAsLong()).reversed())
               .limit(limit)
               .forEach(list::add);
        return list;
    }

    private static long earliest(final long a, final long b) {
        return a == 0L ? b : b == 0L ? a : Math.min(a, b);
    }

    private static void add(final JsonObject into, final JsonObject counts) {
        counts.entrySet().forEach(count -> into.addProperty(count.getKey(),
            (into.has(count.getKey()) ? into.get(count.getKey()).getAsLong() : 0L) + count.getValue().getAsLong()));
    }

    private static JsonObject child(final JsonObject parent, final String name) {
        if (!parent.has(name)) {
            parent.add(name, new JsonObject());
        }
        return parent.getAsJsonObject(name);
    }

    /**
     * @param peer the node's admin address; for the node that took the request, its own, null when the cluster registry
     *             does not list it
     */
    private static JsonObject node(final String peer, final String nodeId, final String status) {
        final JsonObject node = new JsonObject();
        if (peer != null) {
            node.addProperty("peer", peer);
        }
        node.addProperty("nodeId", nodeId);
        node.addProperty("status", status);
        return node;
    }

    private static JsonObject unreachable(final PeerOutcome<?> peer) {
        final JsonObject node = new JsonObject();
        node.addProperty("peer", peer.getPeer());
        node.addProperty("status", "unreachable");
        node.addProperty("error", peer.getError());
        return node;
    }

    private static JsonObject slice(final String peer, final String nodeId, final JsonObject summary) {
        final JsonObject node = node(peer, nodeId, "ok");
        node.add("state", summary.get("state"));
        node.add("roundId", summary.get("roundId"));
        node.add("matched", summary.get("matched"));
        return node;
    }

    private static HttpResponse json(final HttpStatus status, final JsonObject body) {
        return HttpResponse.of(status, MediaType.JSON_UTF_8, GSON.toJson(body));
    }

    private static HttpResponse error(final HttpStatus status, final String code, final String message) {
        final JsonObject body = new JsonObject();
        body.addProperty("status", "error");
        body.addProperty("code", code);
        body.addProperty("message", message);
        return json(status, body);
    }
}
