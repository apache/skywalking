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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.PairingDebugGate;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.PairingDebugListener;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.PairingFact;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.PairingMatch;

/**
 * This node's pairing capture, one per node and shared by every reader: no sessions. Off until someone starts it, it
 * listens on the pairing's gate while it is read: every read of what it found is a ping, and it stops
 * {@link #IDLE_MILLIS} after the latest one, or when someone stops it. Over the halves this node receives
 * as the owner of their traces, it keeps three lists: the peers matched, each to the server that answered it, with
 * whether a later match moved it to another server; the peers whose caller halves left the buffer without their pair;
 * and the servers whose callee halves did. Each list keeps its most recently seen entries, an unmatched one the
 * latest of its trace ids. Each start of a capture that is off begins a round, under the id and start the start gives
 * every node; a start while it runs keeps its round; a stop, or the expiry, releases it, as a DSL debug session's
 * records go when it stops: a client keeps what it read.
 */
public final class PairingCapture {
    /**
     * Entries kept in each list on one node; the least recently seen goes first.
     */
    public static final int MAX_ENTRIES = 1000;
    /**
     * The latest distinct trace ids an unmatched entry keeps, and the distinct callers or rules an entry names.
     */
    public static final int NAMES_KEPT = 5;
    /**
     * How long the capture runs after the latest read.
     */
    public static final long IDLE_MILLIS = 60_000L;

    private final PairingDebugGate gate;
    /**
     * The running round, null while it is off. Every change and every read holds the lock, so start, read, stop,
     * expiry, status and the summary see one round.
     */
    private Round round;
    private long lastReadMillis;

    public PairingCapture(final PairingDebugGate gate) {
        this.gate = gate;
    }

    /**
     * Turn the capture on, as a new round when it was off; a start counts as a read. A capture already running keeps
     * its round, whose id and start the returned status names, so a start reaching it again joins nothing new.
     *
     * @param roundId     the new round's id, the same on every node a start reaches
     * @param startMillis the new round's start, the same on every node a start reaches
     * @return the capture's status after the start, its round's id and start among it
     */
    public synchronized JsonObject start(final long nowMillis, final String roundId, final long startMillis) {
        expire(nowMillis);
        lastReadMillis = Math.max(lastReadMillis, nowMillis);
        if (round == null) {
            round = new Round(roundId, startMillis);
            gate.add(round);
        }
        return status();
    }

    /**
     * A read of what the capture found, which keeps it running unless it has idled past its expiry.
     */
    public synchronized void read(final long nowMillis) {
        expire(nowMillis);
        if (round != null) {
            lastReadMillis = Math.max(lastReadMillis, nowMillis);
        }
    }

    /**
     * Turn the capture off and release what it found. The round's listener leaves the gate with it, so a half the
     * pairing reports while it stops lands in the released round, never in a later one.
     *
     * @return the capture's status after the stop
     */
    public synchronized JsonObject stop() {
        if (round != null) {
            gate.remove(round);
            round = null;
            lastReadMillis = 0L;
        }
        return status();
    }

    /**
     * Turn the capture off when nothing has read it for {@link #IDLE_MILLIS}.
     */
    public synchronized void expire(final long nowMillis) {
        if (round != null && nowMillis - lastReadMillis >= IDLE_MILLIS) {
            stop();
        }
    }

    /**
     * @return {@code capturing}, or {@code off}
     */
    public synchronized String state() {
        return round != null ? "capturing" : "off";
    }

    /**
     * @return whether it is on, and while it is, its round's id and start and when it was last read
     */
    public synchronized JsonObject status() {
        final JsonObject status = new JsonObject();
        status.addProperty("state", state());
        status.addProperty("roundId", round == null ? "" : round.id);
        status.addProperty("startTime", round == null ? 0L : round.startMillis);
        status.addProperty("lastReadTime", lastReadMillis);
        status.addProperty("idleMillis", IDLE_MILLIS);
        return status;
    }

    /**
     * @return this node's state, counts and lists, of one round; empty while it is off
     */
    public synchronized JsonObject summary() {
        final JsonObject summary = status();
        (round == null ? new Found() : round.found).addTo(summary);
        return summary;
    }

    /**
     * One round: from a start to its stop, under one id. Each round listens on the gate by itself.
     */
    @RequiredArgsConstructor
    private static final class Round implements PairingDebugListener {
        private final String id;
        private final long startMillis;
        private final Found found = new Found();

        @Override
        public void received(final PairingFact half, final PairingMatch match) {
            found.received(half, match);
        }

        @Override
        public void leftUnmatched(final PairingFact half, final String cause) {
            found.leftUnmatched(half, cause);
        }
    }

    /**
     * What one round found. Each update and the summary's copy hold its lock, so a summary's counts agree with its
     * rows; the lists' own locks are taken inside it.
     */
    private static final class Found {
        private final LongAdder callersReceived = new LongAdder();
        private final LongAdder calleesReceived = new LongAdder();
        private final LongAdder matched = new LongAdder();
        /**
         * Halves that left the buffer unmatched, by role and cause.
         */
        private final Map<String, Map<String, LongAdder>> unmatched = new ConcurrentHashMap<>();
        /**
         * By the peer a caller dialed.
         */
        private final Recent<String, Mapping> mappings = new Recent<>();
        /**
         * Caller halves by the peer they dialed; callee halves by their server.
         */
        private final Recent<String, Unmatched> unmatchedPeers = new Recent<>();
        private final Recent<String, Unmatched> unmatchedServers = new Recent<>();

        /**
         * The time is taken under the lock, so a row's times follow the order its updates land in.
         */
        private synchronized void received(final PairingFact half, final PairingMatch match) {
            final long now = System.currentTimeMillis();
            (half.isServer() ? calleesReceived : callersReceived).increment();
            if (match == null) {
                return;
            }
            matched.increment();
            mappings.update(match.getCaller().getPeer(), peer -> new Mapping(), mapping -> mapping.add(match, now));
        }

        private synchronized void leftUnmatched(final PairingFact half, final String cause) {
            final long now = System.currentTimeMillis();
            unmatched.computeIfAbsent(half.isServer() ? "callee" : "caller", r -> new ConcurrentHashMap<>())
                     .computeIfAbsent(cause, c -> new LongAdder()).increment();
            if (half.isServer()) {
                unmatchedServers.update(half.getService(), service -> new Unmatched(), entry -> entry.add(half, cause, now));
            } else {
                unmatchedPeers.update(half.getPeer(), peer -> new Unmatched(), entry -> entry.add(half, cause, now));
            }
        }

        private synchronized void addTo(final JsonObject summary) {
            final JsonObject received = new JsonObject();
            received.addProperty("caller", callersReceived.sum());
            received.addProperty("callee", calleesReceived.sum());
            summary.add("received", received);
            summary.addProperty("matched", matched.sum());
            final JsonObject left = new JsonObject();
            unmatched.forEach((role, causes) -> {
                final JsonObject byCause = new JsonObject();
                causes.forEach((cause, count) -> byCause.addProperty(cause, count.sum()));
                left.add(role, byCause);
            });
            summary.add("unmatched", left);
            final JsonArray matchedPeers = new JsonArray();
            mappings.entries().forEach((peer, mapping) -> matchedPeers.add(mapping.toJson(peer)));
            summary.add("matchedPeers", matchedPeers);
            final JsonArray peers = new JsonArray();
            unmatchedPeers.entries().forEach((peer, entry) -> peers.add(entry.toJson("peer", peer, "callers")));
            summary.add("unmatchedPeers", peers);
            final JsonArray servers = new JsonArray();
            unmatchedServers.entries().forEach((server, entry) -> servers.add(entry.toJson("server", server, "rules")));
            summary.add("unmatchedServers", servers);
        }
    }

    /**
     * Entries in the order they were last seen, the least recent dropped past {@link #MAX_ENTRIES}.
     */
    private static final class Recent<K, V> {
        private final LinkedHashMap<K, V> map = new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
                return size() > MAX_ENTRIES;
            }
        };

        /**
         * Create the entry when absent and update it, both under this list's lock, so a reader never takes an entry
         * before its first update. The entry's own lock is taken inside this one, never the other way round.
         */
        private synchronized void update(final K key, final Function<K, V> create, final Consumer<V> change) {
            change.accept(map.computeIfAbsent(key, create));
        }

        private synchronized Map<K, V> entries() {
            return new LinkedHashMap<>(map);
        }
    }

    /**
     * The server a peer was matched to, and the server before it when a later match moved it.
     */
    private static final class Mapping {
        private final LongAdder count = new LongAdder();
        private final Map<String, LongAdder> outcomes = new ConcurrentHashMap<>();
        private final Names callers = new Names();
        private String server;
        private String previousServer;
        /**
         * When the match moved it off the previous server.
         */
        private long updatedTime;
        /**
         * When the previous server was matched last, which orders it among the servers other nodes saw.
         */
        private long previousTime;
        /**
         * The address the latest match composed; null when it composed none.
         */
        private String address;
        private long firstTime;
        private long lastTime;

        private synchronized void add(final PairingMatch match, final long now) {
            count.increment();
            outcomes.computeIfAbsent(match.getOutcome().getCode(), o -> new LongAdder()).increment();
            final String callee = match.getCallee().getService();
            if (server == null) {
                firstTime = now;
            } else if (!server.equals(callee)) {
                previousServer = server;
                previousTime = lastTime;
                updatedTime = now;
            }
            server = callee;
            lastTime = now;
            callers.add(match.getCaller().getService());
            address = match.getAddress();
        }

        private synchronized JsonObject toJson(final String peer) {
            final JsonObject json = new JsonObject();
            json.addProperty("peer", peer);
            json.addProperty("server", server);
            if (previousServer != null) {
                json.addProperty("previousServer", previousServer);
                json.addProperty("updatedTime", updatedTime);
                json.addProperty("previousTime", previousTime);
            }
            json.add("callers", callers.toJson());
            if (address != null) {
                json.addProperty("address", address);
            }
            json.addProperty("count", count.sum());
            final JsonObject byOutcome = new JsonObject();
            outcomes.forEach((outcome, n) -> byOutcome.addProperty(outcome, n.sum()));
            json.add("outcomes", byOutcome);
            json.addProperty("firstTime", firstTime);
            json.addProperty("lastTime", lastTime);
            return json;
        }
    }

    /**
     * The latest {@link #NAMES_KEPT} distinct names, the latest first.
     */
    private static final class Names {
        private final Deque<String> names = new ArrayDeque<>();

        private void add(final String name) {
            names.remove(name);
            names.addFirst(name);
            if (names.size() > NAMES_KEPT) {
                names.removeLast();
            }
        }

        private JsonArray toJson() {
            final JsonArray json = new JsonArray();
            names.forEach(json::add);
            return json;
        }
    }

    /**
     * Halves that left the buffer without their pair: caller halves of one peer, or callee halves of one server.
     */
    private static final class Unmatched {
        private final Map<String, Long> causes = new LinkedHashMap<>();
        private final Names traceIds = new Names();
        /**
         * The callers that dialed the peer, or the rules of the server.
         */
        private final Names names = new Names();
        private long count;
        private long firstTime;
        private long lastTime;

        private synchronized void add(final PairingFact half, final String cause, final long now) {
            if (count == 0) {
                firstTime = now;
            }
            count++;
            causes.merge(cause, 1L, Long::sum);
            lastTime = now;
            traceIds.add(half.getTraceId());
            names.add(half.isServer() ? half.getRuleName() : half.getService());
        }

        private synchronized JsonObject toJson(final String keyName, final String key, final String namesName) {
            final JsonObject json = new JsonObject();
            json.addProperty(keyName, key);
            json.add(namesName, names.toJson());
            json.addProperty("count", count);
            final JsonObject byCause = new JsonObject();
            causes.forEach(byCause::addProperty);
            json.add("causes", byCause);
            json.addProperty("firstTime", firstTime);
            json.addProperty("lastTime", lastTime);
            json.add("traceIds", traceIds.toJson());
            return json;
        }
    }
}
