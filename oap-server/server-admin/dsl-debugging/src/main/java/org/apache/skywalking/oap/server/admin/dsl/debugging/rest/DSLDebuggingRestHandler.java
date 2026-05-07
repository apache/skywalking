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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.Blocking;
import com.linecorp.armeria.server.annotation.Default;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.Post;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.DSLDebuggingClusterClient;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.PeerOutcome;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterExecutionRecord;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterSample;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.CollectDebugSamplesAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.InstallDebugSessionAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.InstallState;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopByClientIdAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.StopDebugSessionAck;
import org.apache.skywalking.oap.server.admin.dsl.debugging.module.DSLDebuggingModuleConfig;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugSession;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugSessionRegistry;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.InstallOutcome;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.ExecutionRecord;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.Granularity;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.Sample;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.SessionLimits;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;

/**
 * Session control plane for the DSL debug API. Routes mount on the shared
 * {@code admin-server} HTTP host under {@code /dsl-debugging/*}.
 *
 * <h2>Wire shape (post-reshape)</h2>
 * <pre>
 *   GET /dsl-debugging/session/{id}
 *     {
 *       sessionId, capturedAt, ruleKey,
 *       rule: { name, dsl },          // verbatim rule source, once at envelope level
 *       nodes: [{
 *         nodeId, status, captured, totalBytes,
 *         records: [{                  // one record = one execution
 *           startedAtMs,
 *           samples: [{                // one sample per pipeline step
 *             sourceText,              // verbatim DSL fragment
 *             continueOn,              // did the rule keep going past this step?
 *             payload,                 // hand-built JSON via toJson()
 *             sourceLine               // 1-based source line, 0 if N/A
 *           }, ...]
 *         }, ...]
 *       }, ...]
 *     }
 * </pre>
 *
 * <p>No {@code stage} on samples and no per-record {@code content} — the
 * envelope's {@code rule.dsl} carries the verbatim source once, and each
 * sample's {@code sourceText} is the locator inside the rule.
 *
 * <p>Catalog parsing is the only string-typed boundary: the handler resolves
 * the wire-name to a typed {@link Catalog} before constructing
 * {@link RuleKey}, so the rest of the call stack stays on typed objects.
 */
@Blocking
public class DSLDebuggingRestHandler {

    private static final Gson GSON = new Gson();

    private final DSLDebuggingModuleConfig moduleConfig;
    private final DebugSessionRegistry sessionRegistry;
    private final DSLDebuggingClusterClient clusterClient;
    private final String selfNodeId;

    public DSLDebuggingRestHandler(final DSLDebuggingModuleConfig moduleConfig,
                                   final DebugSessionRegistry sessionRegistry,
                                   final DSLDebuggingClusterClient clusterClient,
                                   final String selfNodeId) {
        this.moduleConfig = moduleConfig;
        this.sessionRegistry = sessionRegistry;
        this.clusterClient = clusterClient;
        this.selfNodeId = selfNodeId == null ? "" : selfNodeId;
    }

    @Post("/dsl-debugging/session")
    public HttpResponse createSession(@Param("catalog") final String catalog,
                                      @Param("name") final String name,
                                      @Param("ruleName") final String ruleName,
                                      @Param("clientId") final String clientId,
                                      @Param("granularity") @Default("") final String granularityParam,
                                      final HttpData body) {
        if (!moduleConfig.isInjectionEnabled()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE,
                         "injection_disabled",
                         "DSL debug capture is permanently disabled by configuration.");
        }
        final Catalog parsed = parseCatalogOrNull(catalog);
        if (parsed == null) {
            return error(HttpStatus.BAD_REQUEST, "invalid_catalog",
                         "Unknown catalog '" + catalog + "'. Debug-supported catalogs: "
                             + supportedCatalogs() + ". Catalogs from receivers without a "
                             + "static-loader binding (zabbix-rules, envoy-rules, ...) are "
                             + "not debuggable today.");
        }
        if (name == null || name.isEmpty() || ruleName == null || ruleName.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "missing_param",
                         "Both 'name' and 'ruleName' query params are required.");
        }
        if (clientId == null || clientId.isEmpty()) {
            // The "one clientId → one active session" cleanup contract relies on a
            // non-empty clientId so stopByClientId() can match the prior session and
            // free the slot. A missing/empty clientId would silently let repeated
            // POSTs accumulate distinct sessions toward the active-session ceiling.
            return error(HttpStatus.BAD_REQUEST, "missing_param",
                         "'clientId' query param is required so the prior session for "
                             + "this client can be cleaned up before installing a new "
                             + "one. Mint a stable id per debug context (browser tab, "
                             + "CLI invocation) and reuse it across start / poll / stop.");
        }
        final RuleKey ruleKey = new RuleKey(parsed, name, ruleName);
        final SessionLimits limits;
        try {
            final SessionLimits parsedLimits = parseLimits(body);
            // Query param wins over body — operators expect URL-visible flags to dominate.
            final Granularity effective = granularityParam != null && !granularityParam.isEmpty()
                ? Granularity.ofWireName(granularityParam)
                : parsedLimits.getGranularity();
            limits = new SessionLimits(
                parsedLimits.getRecordCap(), parsedLimits.getRetentionMillis(), effective);
        } catch (final IllegalArgumentException iae) {
            return error(HttpStatus.BAD_REQUEST, "invalid_limits", iae.getMessage());
        }

        // Cluster-scope clientId cleanup first (SWIP §6) — best-effort, missed
        // peers self-clean via retention timeout. Local sweep covers the case
        // where this node also still holds the prior session for this clientId,
        // since the cluster fan-out skips self.
        //
        // Concurrent POSTs with the same clientId at the SAME node may both
        // observe "no prior session" and both install — the per-node race is
        // not synchronised here because the consequences are bounded: orphan
        // sessions auto-clear on retention timeout (default 5 min) and the
        // active-session ceiling per node ({@code MAX_ACTIVE_SESSIONS = 200})
        // caps the worst-case heap. On peers, each install's
        // broadcastStopByClientId stops the prior, so only the latest session
        // survives cluster-wide.
        final List<String> localStopped = sessionRegistry.stopByClientId(clientId);
        final List<PeerOutcome<StopByClientIdAck>> cleanupOutcomes =
            clusterClient.broadcastStopByClientId(clientId);

        // Mint the sessionId locally so peers and local install share it. Local
        // install may legitimately return NOT_LOCAL on a multi-node cluster
        // where the rule is loaded on a different OAP — the install only fails
        // overall when nobody (local OR any peer) reports INSTALLED.
        final String sessionId = UUID.randomUUID().toString();
        final InstallOutcome localInstall;
        try {
            localInstall = sessionRegistry.installWithId(sessionId, ruleKey, clientId, limits);
        } catch (final IllegalStateException ise) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "registry_misconfigured",
                         ise.getMessage());
        }
        final List<PeerOutcome<InstallDebugSessionAck>> installOutcomes =
            clusterClient.broadcastInstall(sessionId, clientId, ruleKey, limits);

        // Precedence:
        //   1. Any node INSTALLED      → 200 success (the session is live cluster-wide
        //                                even if some other nodes 404'd or 429'd)
        //   2. Else any rule-owning node TOO_MANY_SESSIONS → 429 (cluster fully booked)
        //   3. Else everyone NOT_LOCAL → 404 rule_not_found
        // This gives operators a session as long as ONE node accepted, and only fails
        // when all nodes that own the rule are at capacity, or no node owns it at all.
        final boolean localInstalled = localInstall.getStatus() == InstallOutcome.Status.INSTALLED;
        final boolean localTooMany = localInstall.getStatus() == InstallOutcome.Status.TOO_MANY_SESSIONS;
        boolean anyPeerInstalled = false;
        boolean anyPeerTooMany = false;
        for (final PeerOutcome<InstallDebugSessionAck> outcome : installOutcomes) {
            if (!outcome.isOk()) {
                continue;
            }
            switch (outcome.getAck().getState()) {
                case INSTALLED:
                case ALREADY_INSTALLED:
                    anyPeerInstalled = true;
                    break;
                case TOO_MANY_SESSIONS:
                    anyPeerTooMany = true;
                    break;
                default:
                    break;
            }
        }
        if (!localInstalled && !anyPeerInstalled) {
            // Best-effort cleanup of any peer that may have installed but whose
            // ack didn't reach us within the deadline (slow peer, network blip).
            // Without this sweep, a silent-success peer keeps capturing until
            // retention timeout while the client thinks the install failed.
            clusterClient.broadcastStop(sessionId);
            if (localTooMany || anyPeerTooMany) {
                return errorWithPeers(HttpStatus.TOO_MANY_REQUESTS, "too_many_sessions",
                                      "Every reachable OAP node that owns rule " + ruleKey
                                          + " is at its active-session ceiling ("
                                          + DebugSessionRegistry.MAX_ACTIVE_SESSIONS
                                          + " per node). Stop an existing session via "
                                          + "POST /dsl-debugging/session/{id}/stop or wait "
                                          + "for one to expire. Per-peer install state is "
                                          + "in peers[].",
                                      installOutcomes);
            }
            return errorWithPeers(HttpStatus.NOT_FOUND, "rule_not_found",
                                  "No live DSL artifact bound to rule " + ruleKey
                                      + " on any reachable OAP node. Possible causes: "
                                      + "rule never loaded (static-loader doesn't claim "
                                      + "this name), rule was inactivated via "
                                      + "runtime-rule, or the cluster hasn't compiled "
                                      + "it yet. Per-peer install state is in peers[].",
                                  installOutcomes);
        }

        final long now = System.currentTimeMillis();
        final long createdAt = localInstalled
            ? localInstall.getSession().getCreatedAtMillis() : now;
        final long retentionDeadline = localInstalled
            ? localInstall.getSession().getRetentionDeadlineMillis()
            : now + limits.getRetentionMillis();

        // Summary: how many nodes installed vs how many are reachable. Lets an
        // operator see at a glance "session live on N of M OAPs" without parsing
        // the per-peer peers[] array. The per-peer detail (with each node's id
        // and ack state) stays in peers[].
        int created = localInstalled ? 1 : 0;
        for (final PeerOutcome<InstallDebugSessionAck> outcome : installOutcomes) {
            if (outcome.isOk()) {
                final InstallState state = outcome.getAck().getState();
                if (state == InstallState.INSTALLED || state == InstallState.ALREADY_INSTALLED) {
                    created++;
                }
            }
        }
        final int totalNodes = 1 + installOutcomes.size();

        final JsonObject payload = new JsonObject();
        payload.addProperty("sessionId", sessionId);
        payload.addProperty("clientId", clientId);
        payload.add("ruleKey", ruleKeyToJson(ruleKey));
        payload.addProperty("createdAt", createdAt);
        payload.addProperty("retentionDeadline", retentionDeadline);
        payload.addProperty("granularity", limits.getGranularity().wireName());
        payload.addProperty("localInstalled", localInstalled);
        final JsonObject installSummary = new JsonObject();
        installSummary.addProperty("created", created);
        installSummary.addProperty("total", totalNodes);
        payload.add("installed", installSummary);
        payload.add("peers", installPeersToJson(installOutcomes));
        final JsonObject priorCleanup = new JsonObject();
        final JsonArray localStoppedIds = new JsonArray();
        for (final String id : localStopped) {
            localStoppedIds.add(id);
        }
        final JsonObject localCleanup = new JsonObject();
        localCleanup.addProperty("nodeId", selfNodeId);
        localCleanup.addProperty("stoppedCount", localStopped.size());
        localCleanup.add("stoppedSessionIds", localStoppedIds);
        priorCleanup.add("local", localCleanup);
        priorCleanup.add("peers", cleanupPeersToJson(cleanupOutcomes));
        payload.add("priorCleanup", priorCleanup);
        return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, GSON.toJson(payload));
    }

    @Get("/dsl-debugging/session/{id}")
    public HttpResponse getSession(@Param("id") final String id) {
        final DebugSession local = sessionRegistry.find(id);
        // No-local-session is fine — the receiving node may have minted the id and
        // forwarded to peers. Carry on with a "local: not_local" entry; cluster
        // collect carries the rest.
        final List<PeerOutcome<CollectDebugSamplesAck>> peerOutcomes =
            clusterClient.broadcastCollect(id);

        // 404 contract: when EVERY reachable node has explicitly disowned the
        // session ("not_local"), the id is genuinely unknown. A peer that
        // FAILED (channel timeout, deadline exceeded, etc.) has NOT proven the
        // session is absent — the session might be alive there but its ack
        // didn't arrive. To avoid reporting a transient peer failure as a
        // permanent 404, the 404 only fires when every peer responded OK with
        // "not_local"; if any peer outcome is FAILED the response is partial
        // 200 with the failed peer reflected in nodes[] (status=unreachable),
        // letting operators retry and see the cluster slice when the peer
        // recovers.
        if (local == null) {
            boolean anyPeerKnowsSession = false;
            boolean anyPeerFailed = false;
            for (final PeerOutcome<CollectDebugSamplesAck> outcome : peerOutcomes) {
                if (!outcome.isOk()) {
                    anyPeerFailed = true;
                    continue;
                }
                if (!"not_local".equals(outcome.getAck().getStatus())) {
                    anyPeerKnowsSession = true;
                    break;
                }
            }
            if (!anyPeerKnowsSession && !anyPeerFailed) {
                return error(HttpStatus.NOT_FOUND, "session_not_found",
                             "Session id '" + id + "' is unknown to every reachable "
                                 + "OAP node — never created, retention timed out, or "
                                 + "already stopped.");
            }
        }

        final JsonObject payload = new JsonObject();
        payload.addProperty("sessionId", id);
        // One slice-level timestamp at GET-time. Probes fire sub-millisecond, so
        // per-record stamps are noise — array order preserves intra-execution
        // ordering. The UI uses this to render "fetched at ...".
        payload.addProperty("capturedAt", System.currentTimeMillis());
        if (local != null) {
            payload.add("ruleKey", ruleKeyToJson(local.getRuleKey()));
        }
        final JsonArray nodes = new JsonArray();
        nodes.add(localNodeSlice(local));
        for (final PeerOutcome<CollectDebugSamplesAck> outcome : peerOutcomes) {
            nodes.add(peerNodeSlice(outcome));
        }
        payload.add("nodes", nodes);
        return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, GSON.toJson(payload));
    }

    @Post("/dsl-debugging/session/{id}/stop")
    public HttpResponse stopSession(@Param("id") final String id) {
        final boolean localStopped = sessionRegistry.uninstall(id);
        final List<PeerOutcome<StopDebugSessionAck>> peerOutcomes = clusterClient.broadcastStop(id);
        final JsonObject body = new JsonObject();
        body.addProperty("sessionId", id);
        body.addProperty("localStopped", localStopped);
        final JsonArray peers = new JsonArray();
        for (final PeerOutcome<StopDebugSessionAck> outcome : peerOutcomes) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("peer", outcome.getPeerAddress());
            if (outcome.isOk()) {
                entry.addProperty("nodeId", outcome.getAck().getNodeId());
                entry.addProperty("stopped", outcome.getAck().getStopped());
            } else {
                entry.addProperty("ack", "failed");
                entry.addProperty("detail", outcome.getFailure());
            }
            peers.add(entry);
        }
        body.add("peers", peers);
        return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, GSON.toJson(body));
    }

    @Get("/dsl-debugging/sessions")
    public HttpResponse listSessions() {
        final JsonArray list = new JsonArray();
        for (final DebugSession session : sessionRegistry.snapshotActive()) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("sessionId", session.getSessionId());
            entry.addProperty("clientId", session.getClientId());
            entry.add("ruleKey", ruleKeyToJson(session.getRuleKey()));
            entry.addProperty("createdAt", session.getCreatedAtMillis());
            entry.addProperty("retentionDeadline", session.getRetentionDeadlineMillis());
            entry.addProperty("captured", session.getRecorder().isCaptured());
            entry.addProperty("totalBytes", session.getRecorder().totalBytes());
            list.add(entry);
        }
        final JsonObject body = new JsonObject();
        body.add("sessions", list);
        body.addProperty("count", list.size());
        return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, GSON.toJson(body));
    }

    @Get("/dsl-debugging/status")
    public HttpResponse status() {
        final JsonObject body = new JsonObject();
        body.addProperty("module", "dsl-debugging");
        body.addProperty("phase", "phase-4");
        body.addProperty("nodeId", selfNodeId);
        body.addProperty("injectionEnabled", moduleConfig.isInjectionEnabled());
        body.addProperty("activeSessions", sessionRegistry.snapshotActive().size());
        return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, GSON.toJson(body));
    }

    /** Comma-separated list of catalog wire names the debug REST surface accepts. */
    private static String supportedCatalogs() {
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (final Catalog c : Catalog.values()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(c.getWireName());
            first = false;
        }
        return sb.toString();
    }

    private static Catalog parseCatalogOrNull(final String wireName) {
        if (wireName == null) {
            return null;
        }
        try {
            return Catalog.of(wireName);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses the optional JSON body. Throws {@link IllegalArgumentException}
     * with a user-facing message on malformed JSON or out-of-range limits,
     * which the caller maps to {@code 400 invalid_limits}. An empty body
     * stays mapped to {@link SessionLimits#DEFAULT} so a plain
     * {@code POST /dsl-debugging/session?...} without a body just works.
     */
    private SessionLimits parseLimits(final HttpData body) {
        if (body == null || body.length() == 0) {
            return SessionLimits.DEFAULT;
        }
        final JsonObject root;
        try {
            root = JsonParser.parseString(
                new String(body.array(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (final RuntimeException re) {
            throw new IllegalArgumentException(
                "Request body is not valid JSON: " + re.getMessage()
                    + ". Send an empty body for defaults, or a valid JSON object "
                    + "with optional `recordCap`, `retentionMillis`, `granularity` "
                    + "fields.");
        }
        final int recordCap = root.has("recordCap")
            ? root.get("recordCap").getAsInt() : SessionLimits.DEFAULT.getRecordCap();
        final long retention = root.has("retentionMillis")
            ? root.get("retentionMillis").getAsLong()
            : SessionLimits.DEFAULT.getRetentionMillis();
        final Granularity granularity = root.has("granularity")
            ? Granularity.ofWireName(root.get("granularity").getAsString())
            : SessionLimits.DEFAULT.getGranularity();
        // Let SessionLimits validate the bounds — caller surfaces 400 invalid_limits.
        return new SessionLimits(recordCap, retention, granularity);
    }

    private static JsonObject ruleKeyToJson(final RuleKey key) {
        final JsonObject obj = new JsonObject();
        obj.addProperty("catalog", key.getCatalog().getWireName());
        obj.addProperty("name", key.getName());
        obj.addProperty("ruleName", key.getRuleName());
        return obj;
    }

    private JsonObject localNodeSlice(final DebugSession local) {
        final JsonObject node = new JsonObject();
        node.addProperty("nodeId", selfNodeId);
        if (local == null) {
            node.addProperty("status", "not_local");
            node.add("records", new JsonArray());
            return node;
        }
        node.addProperty("status", local.getRecorder().isCaptured() ? "captured" : "ok");
        node.addProperty("captured", local.getRecorder().isCaptured());
        node.addProperty("totalBytes", local.getRecorder().totalBytes());
        node.add("records", recordsToJson(local.getRecorder().snapshotRecords()));
        return node;
    }

    private static JsonObject peerNodeSlice(final PeerOutcome<CollectDebugSamplesAck> outcome) {
        final JsonObject node = new JsonObject();
        node.addProperty("peer", outcome.getPeerAddress());
        if (!outcome.isOk()) {
            node.addProperty("status", "unreachable");
            node.addProperty("detail", outcome.getFailure());
            node.add("records", new JsonArray());
            return node;
        }
        final CollectDebugSamplesAck ack = outcome.getAck();
        node.addProperty("nodeId", ack.getNodeId());
        node.addProperty("status", ack.getStatus());
        node.addProperty("captured", ack.getCaptured());
        node.addProperty("totalBytes", ack.getTotalBytes());
        final JsonArray records = new JsonArray();
        for (final ClusterExecutionRecord r : ack.getRecordsList()) {
            records.add(clusterRecordToJson(r));
        }
        node.add("records", records);
        return node;
    }

    private static JsonArray installPeersToJson(final List<PeerOutcome<InstallDebugSessionAck>> outcomes) {
        final JsonArray arr = new JsonArray();
        for (final PeerOutcome<InstallDebugSessionAck> outcome : outcomes) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("peer", outcome.getPeerAddress());
            if (outcome.isOk()) {
                entry.addProperty("nodeId", outcome.getAck().getNodeId());
                entry.addProperty("ack", outcome.getAck().getState().name());
                if (!outcome.getAck().getDetail().isEmpty()) {
                    entry.addProperty("detail", outcome.getAck().getDetail());
                }
            } else {
                entry.addProperty("ack", "FAILED");
                entry.addProperty("detail", outcome.getFailure());
            }
            arr.add(entry);
        }
        return arr;
    }

    private static JsonArray cleanupPeersToJson(final List<PeerOutcome<StopByClientIdAck>> outcomes) {
        final JsonArray arr = new JsonArray();
        for (final PeerOutcome<StopByClientIdAck> outcome : outcomes) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("peer", outcome.getPeerAddress());
            if (outcome.isOk()) {
                entry.addProperty("nodeId", outcome.getAck().getNodeId());
                entry.addProperty("stoppedCount", outcome.getAck().getStoppedCount());
                final JsonArray ids = new JsonArray();
                outcome.getAck().getStoppedSessionIdsList().forEach(ids::add);
                entry.add("stoppedSessionIds", ids);
            } else {
                entry.addProperty("ack", "failed");
                entry.addProperty("detail", outcome.getFailure());
            }
            arr.add(entry);
        }
        return arr;
    }

    private static JsonArray recordsToJson(final List<ExecutionRecord> records) {
        final JsonArray arr = new JsonArray();
        for (final ExecutionRecord r : records) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("startedAtMs", r.getStartedAtMillis());
            // Per-record dsl: a hot-update mid-session that rotates the rule
            // onto a fresh compiled class would otherwise make the captured
            // records ambiguous. Each record carries the source as it was
            // when the record was published.
            entry.addProperty("dsl", r.getDsl());
            // Per-record structured rule metadata (catalog-specific).
            entry.add("rule", metadataToJson(r.getMetadata()));
            final JsonArray samples = new JsonArray();
            for (final Sample s : r.getSamples()) {
                samples.add(sampleToJson(s));
            }
            entry.add("samples", samples);
            arr.add(entry);
        }
        return arr;
    }

    private static JsonObject metadataToJson(final Map<String, String> metadata) {
        final JsonObject obj = new JsonObject();
        if (metadata == null) {
            return obj;
        }
        for (final Map.Entry<String, String> e : metadata.entrySet()) {
            obj.addProperty(e.getKey(), e.getValue());
        }
        return obj;
    }

    private static JsonObject sampleToJson(final Sample s) {
        final JsonObject entry = new JsonObject();
        if (s.getType() != null) {
            entry.addProperty("type", s.getType());
        }
        entry.addProperty("sourceText", s.getSourceText());
        entry.addProperty("continueOn", s.isContinueOn());
        entry.add("payload", JsonParser.parseString(
            s.getPayloadJson() == null || s.getPayloadJson().isEmpty()
                ? "{}" : s.getPayloadJson()));
        // Only emit sourceLine when it points at a real source line. 0 is
        // the "doesn't apply" sentinel for probes that don't map to one
        // (MAL chain ops, LAL block-mode probes) — omitting keeps the
        // wire shape clean.
        if (s.getSourceLine() > 0) {
            entry.addProperty("sourceLine", s.getSourceLine());
        }
        return entry;
    }

    private static JsonObject clusterRecordToJson(final ClusterExecutionRecord r) {
        final JsonObject entry = new JsonObject();
        entry.addProperty("startedAtMs", r.getStartedAtMs());
        entry.addProperty("dsl", r.getDsl());
        entry.add("rule", metadataToJson(r.getMetadataMap()));
        final JsonArray samples = new JsonArray();
        for (final ClusterSample s : r.getSamplesList()) {
            samples.add(clusterSampleToJson(s));
        }
        entry.add("samples", samples);
        return entry;
    }

    private static JsonObject clusterSampleToJson(final ClusterSample s) {
        final JsonObject entry = new JsonObject();
        if (s.getType() != null && !s.getType().isEmpty()) {
            entry.addProperty("type", s.getType());
        }
        entry.addProperty("sourceText", s.getSourceText());
        entry.addProperty("continueOn", s.getContinueOn());
        entry.add("payload", JsonParser.parseString(
            s.getPayloadJson() == null || s.getPayloadJson().isEmpty()
                ? "{}" : s.getPayloadJson()));
        if (s.getSourceLine() > 0) {
            entry.addProperty("sourceLine", s.getSourceLine());
        }
        return entry;
    }

    private static HttpResponse error(final HttpStatus status, final String code,
                                      final String message) {
        final JsonObject body = new JsonObject();
        body.addProperty("status", "error");
        body.addProperty("code", code);
        body.addProperty("message", message);
        return HttpResponse.of(status, MediaType.JSON_UTF_8, GSON.toJson(body));
    }

    /**
     * Error response for install-time failures that need to surface per-peer outcomes
     * — when the message advertises {@code peers[]} the body has to actually carry it.
     */
    private static HttpResponse errorWithPeers(final HttpStatus status, final String code,
                                                final String message,
                                                final List<PeerOutcome<InstallDebugSessionAck>> outcomes) {
        final JsonObject body = new JsonObject();
        body.addProperty("status", "error");
        body.addProperty("code", code);
        body.addProperty("message", message);
        body.add("peers", installPeersToJson(outcomes));
        return HttpResponse.of(status, MediaType.JSON_UTF_8, GSON.toJson(body));
    }
}
