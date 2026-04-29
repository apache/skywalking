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

package org.apache.skywalking.oap.server.receiver.runtimerule.rest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.classloader.DSLClassLoaderManager;
import org.apache.skywalking.oap.server.core.classloader.RuleClassLoader;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.core.management.runtimerule.RuntimeRule;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.rule.ext.StaticRuleRegistry;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.apply.DSLDelta;
import org.apache.skywalking.oap.server.receiver.runtimerule.apply.DeltaClassifier;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.Classification;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.MainRouter;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.RuntimeRuleClusterClient;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.ForwardResponse;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.SuspendAck;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.v1.SuspendState;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLRuntimeDelete;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLScriptKey;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.SuspendResult;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState;
import org.apache.skywalking.oap.server.receiver.runtimerule.util.ContentHash;

/**
 * Armeria HTTP handler for the runtime rule admin surface.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code /addOrUpdate} — raw body in, validate, compile-check via
 *       {@link DeltaClassifier}, reject shape-breaking edits without
 *       {@code allowStorageChange=true}, upsert via {@link RuntimeRuleManagementDAO#save}
 *       (per-backend explicit upsert; the generic ManagementDAO insert path was removed
 *       because it never persisted on BanyanDB and silently no-op'd updates on ES/JDBC),
 *       then drive the per-file apply inline via
 *       {@link DSLManager#applyNowForRuleFile}. Returns the resolved status (structural_applied,
 *       filter_only_applied, ddl_verify_failed, compile_failed, no_change,
 *       storage_change_requires_explicit_approval).</li>
 *   <li>{@code /inactivate} — the soft-pause path. Broadcasts Suspend, flips the row to
 *       INACTIVE, runs the OAP-internal teardown under
 *       {@link StorageManipulationOpt#localCacheOnly} via
 *       {@link DSLManager#applyNowForRuleFile}: dispatch handlers unregistered, prototypes
 *       and Models cleared, alarm windows reset. The BanyanDB measure and its data are
 *       explicitly preserved so reactivation via {@code /addOrUpdate} on the INACTIVE row
 *       is cheap and lossless. Peers observe the INACTIVE row on their next tick and run
 *       the same OAP-internal teardown. The inactive rule still HOLDS its metric / rule
 *       names per the soft-pause contract — another file claiming any of those names is
 *       rejected by the cross-file ownership guard.</li>
 *   <li>{@code /delete} — the destructive path. Requires the rule to already be INACTIVE
 *       (returns HTTP 409 {@code requires_inactivate_first} otherwise) — the two-step
 *       {@code /inactivate → /delete} workflow is enforced. {@code /delete} drives
 *       {@link DSLRuntimeDelete}: re-registers prototypes locally under
 *       {@code localCacheOnly} so the cascade has Models to walk, then runs the unregister
 *       path under {@code fullInstall} so the listener chain fires BanyanDB delete-measure
 *       on the live measure. Backend-drop failure aborts the row
 *       removal — an orphaned measure with no row left to retry is never possible. After
 *       the row is gone, if a static version exists on disk the rule reverts to that on
 *       the next dslManager tick.</li>
 *   <li>{@code /list} returns an NDJSON view of every row merged with the dslManager's
 *       per-node {@link DSLRuntimeState}. {@code /dump} streams a tar.gz of every row plus a
 *       manifest so the entire admin surface can be backed up and restored.</li>
 *   <li>{@code GET /runtime/rule?catalog=&name=} fetches a single rule's YAML with DAO
 *       row → static fallback → 404 lookup. Default raw YAML; JSON envelope on
 *       {@code Accept: application/json}. {@code ETag} / {@code If-None-Match} → 304.
 *       {@code GET /runtime/rule/bundled?catalog=} lists every static rule for the
 *       catalog with an {@code overridden} flag joined from runtime rows.</li>
 * </ul>
 *
 * <p>Catalog shortcut routes ({@code /runtime/mal/otel/...}, {@code /runtime/mal/log/...},
 * {@code /runtime/lal/...}) normalize into the canonical handler methods so every entry shape
 * reuses the same validation + persistence path.
 */
@Slf4j
public class RuntimeRuleService {

    /** Catalog membership is data-driven through the engine registry — a catalog is valid
     *  iff some registered engine claims it via {@link RuleEngine#supportedCatalogs}. */
    private boolean isValidCatalog(final String catalog) {
        return dslManager.getEngineRegistry().forCatalog(catalog) != null;
    }

    /** Set of catalogs accepted by REST, computed from the engine registry. Used only in
     *  error messages where the operator wants to see the recognised list. */
    private Set<String> validCatalogs() {
        final Set<String> out = new LinkedHashSet<>();
        for (final RuleEngine<?> engine : dslManager.getEngineRegistry().engines()) {
            out.addAll(engine.supportedCatalogs());
        }
        return out;
    }

    /**
     * Per-file lock acquisition timeout on the REST path. 35 s — covers the typical upper
     * bound of a full STRUCTURAL workflow (classify + compile + DDL + persist on BanyanDB)
     * with a margin. Requests that exceed this are backed up beyond what normal operator
     * flow should produce; the handler returns 409 instead of parking the Armeria thread.
     */
    private static final long REST_LOCK_TIMEOUT_MS = 35_000L;

    /**
     * Name segments are {@code [A-Za-z0-9._-]+}, separated by {@code /}. No leading slash, no
     * {@code ..}, no empty segments, no backslash. Matches what the filesystem loader tolerates
     * and blocks path-traversal attempts on the dump tar + DB key.
     */
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*$");

    private final ModuleManager moduleManager;
    private final DSLManager dslManager;
    private final RuntimeRuleClusterClient clusterClient;
    /**
     * Deadline for the forward-to-main RPC. Longer than the Suspend / Resume deadlines
     * because the forwarded workflow includes compile + DDL + persist. Tunable via the
     * module config.
     */
    private final long forwardRpcDeadlineMs;
    /**
     * Resolved lazily on first routing decision. Null means this OAP has no cluster wiring
     * (embedded topology, early boot), in which case self always handles the write.
     */
    private volatile RemoteClientManager remoteClientManager;

    public RuntimeRuleService(final ModuleManager moduleManager,
                                  final DSLManager dslManager,
                                  final RuntimeRuleClusterClient clusterClient,
                                  final long forwardRpcDeadlineMs) {
        this.moduleManager = moduleManager;
        this.dslManager = Objects.requireNonNull(dslManager,
            "dslManager — runtime-rule REST handler cannot operate without it");
        this.clusterClient = clusterClient;
        this.forwardRpcDeadlineMs = forwardRpcDeadlineMs;
    }

    /**
     * Route a write request. Three possible outcomes:
     * <ul>
     *   <li>Self is main (or cluster empty) → returns null; caller runs the local workflow.</li>
     *   <li>Self is not main AND this request was NOT already forwarded → forward via gRPC
     *       to the main and relay the response. The operator sees a transparent result.</li>
     *   <li>Self is not main AND this request WAS forwarded (the incoming sender's cluster
     *       view disagreed with ours) → return HTTP 421 to bound ping-pong at one hop.
     *       Operator-facing signal that the cluster view is split.</li>
     * </ul>
     *
     * @param forwarded true when the request arrived via the cluster Forward RPC, false
     *                  for a direct HTTP caller. See {@link #executeAddOrUpdate} for where
     *                  {@code forwarded} is set.
     * @return non-null HttpResponse when routing decided the outcome (either forwarded or
     *         fail-safe 421); null when the caller should proceed with the local workflow.
     */
    private HttpResponse routeOrNull(final String catalog, final String name,
                                      final String operation, final byte[] body,
                                      final boolean allowStorageChange,
                                      final boolean forceReapply,
                                      final boolean forwarded) {
        final RemoteClientManager rcm = resolveRemoteClientManager();
        // {@link RemoteClientManager} reflects the cluster's current view; an empty peer
        // list means either there's no cluster module wired (single-process) or the
        // refresh momentarily returned no entries. Either way the local node is the
        // operator's authority for this rule, so we proceed with the local workflow —
        // {@link MainRouter#isSelfMain} treats empty as self-main, mirroring null-rcm.
        if (rcm == null || MainRouter.isSelfMain(rcm)) {
            return null; // self is main (or cluster empty) — run local workflow
        }
        final Address main = MainRouter.mainAddress(rcm);
        if (forwarded) {
            // Fail-safe: we got a forwarded request but WE also don't consider ourselves
            // main. Two cluster views disagree. Refuse instead of re-forwarding; operator
            // sees 421 and can investigate.
            final String mainAddr = main == null ? "unknown" : main.toString();
            log.warn("runtime-rule routing conflict: forwarded request {}/{} arrived but "
                + "self is not main (local main={}); refusing to re-forward", catalog, name, mainAddr);
            return HttpResponse.of(HttpStatus.MISDIRECTED_REQUEST, MediaType.JSON_UTF_8,
                routingErrorBody("cluster_view_split", catalog, name, mainAddr,
                    "forwarded request but self is not main under local cluster view; "
                        + "routing misfire or split-brain"));
        }
        // Normal case: forward to the main via gRPC.
        return forwardToMain(main, operation, catalog, name, body,
            allowStorageChange, forceReapply);
    }

    private HttpResponse forwardToMain(final Address mainAddr,
                                        final String operation,
                                        final String catalog, final String name,
                                        final byte[] body,
                                        final boolean allowStorageChange,
                                        final boolean forceReapply) {
        if (clusterClient == null) {
            // Tests may construct a bare handler without a cluster client. Fall back to
            // running locally so the workflow is still exercised.
            log.debug("runtime-rule: no cluster client wired; running {} {}/{} locally",
                operation, catalog, name);
            return null;
        }
        try {
            log.info("runtime-rule routing: forwarding {} {}/{} to main {}",
                operation, catalog, name, mainAddr);
            final ForwardResponse response = clusterClient.forwardToMain(
                mainAddr, operation, catalog, name, body,
                allowStorageChange, forceReapply, forwardRpcDeadlineMs);
            final HttpStatus status = HttpStatus.valueOf(response.getHttpStatus());
            return HttpResponse.of(status, MediaType.JSON_UTF_8, response.getBody());
        } catch (final Throwable t) {
            log.error("runtime-rule routing: forward to main {} failed for {} {}/{}: {}",
                mainAddr, operation, catalog, name, t.getMessage(), t);
            return HttpResponse.of(HttpStatus.BAD_GATEWAY, MediaType.JSON_UTF_8,
                routingErrorBody("forward_failed", catalog, name,
                    mainAddr == null ? "unknown" : mainAddr.toString(),
                    t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
    }

    private RemoteClientManager resolveRemoteClientManager() {
        RemoteClientManager local = remoteClientManager;
        if (local != null) {
            return local;
        }
        if (moduleManager == null) {
            return null;
        }
        try {
            local = moduleManager.find(CoreModule.NAME).provider()
                .getService(RemoteClientManager.class);
            remoteClientManager = local;
            return local;
        } catch (final Throwable t) {
            return null;
        }
    }

    // ---- Cluster-forward dispatch — invoked by RuntimeRuleClusterServiceImpl ----

    /** Mirror of an HTTP response handed back across the cluster-forward RPC. Immutable;
     *  construction is cheap. The cluster service packs it into a {@code ForwardResponse}
     *  for the originating peer. */
    public static final class ForwardResult {
        @lombok.Getter
        private final int httpStatus;
        @lombok.Getter
        private final String jsonBody;

        public ForwardResult(final int httpStatus, final String jsonBody) {
            this.httpStatus = httpStatus;
            this.jsonBody = jsonBody == null ? "" : jsonBody;
        }
    }

    public ForwardResult executeAddOrUpdate(final String catalog, final String name,
                                                   final byte[] body,
                                                   final boolean allowStorageChange,
                                                   final boolean forceReapply) {
        final HttpResponse resp = doAddOrUpdate(catalog, name,
            body == null ? HttpData.empty() : HttpData.copyOf(body),
            allowStorageChange, forceReapply, /* forwarded */ true);
        return toResult(resp);
    }

    public ForwardResult executeInactivate(final String catalog, final String name) {
        return toResult(doInactivate(catalog, name, /* forwarded */ true));
    }

    public ForwardResult executeDelete(final String catalog, final String name,
                                               final String mode) {
        // Cluster forward arrives with the wire string the originator sent. Re-parse here so
        // the typed flow inside the service is uniform; an invalid value at this stage is
        // an internal bug in the originator (the REST handler validates first), so the
        // throw → 500 is appropriate.
        return toResult(doDelete(catalog, name, DeleteMode.of(mode), /* forwarded */ true));
    }

    /**
     * Drain an Armeria {@link HttpResponse} into a {@link ForwardResult}. Blocks on
     * aggregation; safe here because the Forward RPC handler runs on a blocking executor.
     */
    private static ForwardResult toResult(final HttpResponse resp) {
        final AggregatedHttpResponse agg = resp.aggregate().join();
        return new ForwardResult(agg.status().code(), agg.contentUtf8());
    }

    // ----- Canonical routes (raw body + catalog + name query params) -------------------------

    /**
     * Apply or recover a rule. Two control flags layer on top of the raw body:
     * <ul>
     *   <li>{@code allowStorageChange=true} — accept shape-breaking edits that would otherwise
     *       be rejected with 409. Required for any update that drops or re-shapes a backing
     *       measure / storage schema, since the destructive cascade implies data loss for the
     *       affected metric. Routine pushes leave this {@code false}.</li>
     *   <li>{@code force=true} — recovery flag. Bypasses the byte-identical no_change HTTP
     *       short-circuit so re-posting known-good content (typically extracted from a prior
     *       {@code /runtime/rule/dump} tarball) is treated as a fresh apply request: the
     *       persisted row is re-written and any peers stuck mid-Suspend are re-Resumed.
     *       Engine state (compiled DSL, dispatch handlers, schema) is content-keyed, so a
     *       true no-op against a healthy node remains a no-op even with this flag. Use after
     *       a previous push failed and {@code /list} shows a {@code lastApplyError}, or to
     *       break a stuck SUSPENDED state. Combine with {@code allowStorageChange=true}
     *       when the recovery target re-shapes the measure.</li>
     * </ul>
     */
    public HttpResponse addOrUpdate(final Catalog catalog,
                                    final String name,
                                    final String allowStorageChange,
                                    final String force,
                                    final HttpData body) {
        return doAddOrUpdate(catalog.getWireName(), name, body,
            parseFlag(allowStorageChange), parseFlag(force));
    }

    public HttpResponse inactivate(final Catalog catalog,
                                   final String name) {
        return doInactivate(catalog.getWireName(), name);
    }

    public HttpResponse delete(final Catalog catalog,
                               final String name,
                               final DeleteMode mode) {
        return doDelete(catalog.getWireName(), name, mode);
    }

    /** Surface a 400 for an unrecognised {@code mode=} query value. The REST handler
     *  catches the parse failure and routes here so the response shape matches the rest
     *  of the validation 400s. */
    public HttpResponse invalidDeleteMode(final String catalog, final String name,
                                           final String rawMode) {
        return badRequest("invalid_mode", catalog, name,
            "mode must be omitted or one of " + DeleteMode.REVERT_TO_BUNDLED.getWireValue()
                + "; received '" + (rawMode == null ? "" : rawMode) + "'");
    }

    /** Surface a 400 for an unrecognised {@code catalog=} query value. The REST handler
     *  parses the catalog string into a {@link Catalog} enum at the boundary; an unknown
     *  value lands here so the response is uniform with the other validation 400s. */
    public HttpResponse invalidCatalog(final String rawCatalog, final String name) {
        return badRequest("invalid_catalog", rawCatalog, name,
            "catalog must be one of " + validCatalogs());
    }

    public HttpResponse list(final String catalogFilter) {
        // Merged per-node view of what the dslManager has seen, joined with what is actually
        // in storage. Returns a single JSON envelope:
        //   {generatedAt, loaderStats:{active,pending}, rules:[ ... ]}
        // so a UI consumer can JSON.parse() once and an operator can `jq '.rules[]'`.
        //
        // catalogFilter (empty / null => no filter) narrows the output to one catalog —
        // useful when scripting against a single catalog. Validated against the same set as
        // the write endpoints; an unknown non-empty catalog returns 400 instead of an empty
        // body so the operator gets a clear "you typed it wrong" signal.
        final String filter = catalogFilter == null ? "" : catalogFilter.trim();
        if (!filter.isEmpty() && !isValidCatalog(filter)) {
            return badRequest("invalid_catalog", filter, null,
                "catalog must be one of " + validCatalogs());
        }
        final RuntimeRuleManagementDAO dao = resolveDao();
        if (dao == null) {
            return serverError("dao_unavailable", null, null,
                "RuntimeRuleManagementDAO not resolvable — storage module may not be active");
        }
        final List<RuntimeRuleManagementDAO.RuntimeRuleFile> ruleFiles;
        try {
            ruleFiles = dao.getAll();
        } catch (final IOException e) {
            return serverError("list_failed", null, null, e.getMessage());
        }

        // Index local dslManager state by (catalog, name) for O(1) join.
        final Map<String, DSLRuntimeState> localByKey = new HashMap<>();
        for (final Map.Entry<String, AppliedRuleScript> e : dslManager.getRules().entrySet()) {
            final DSLRuntimeState s = e.getValue().getState();
            if (s != null) {
                localByKey.put(e.getKey(), s);
            }
        }

        final JsonArray rows = new JsonArray();
        for (final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile : ruleFiles) {
            if (!filter.isEmpty() && !filter.equals(ruleFile.getCatalog())) {
                continue;
            }
            final String key = DSLScriptKey.key(ruleFile.getCatalog(), ruleFile.getName());
            final DSLRuntimeState local = localByKey.remove(key);
            rows.add(renderListEntry(ruleFile, local));
        }
        // Snapshot entries with no DAO row fall into two buckets:
        //   1. Bundled-only — shipped rule on disk, never operator-overridden. The dslManager
        //      seeded the snapshot from StaticRuleRegistry at boot, and /inactivate + tick
        //      rehydrate keep it in sync. These are healthy — status=BUNDLED.
        //   2. True orphans — runtime row was just deleted, the dslManager hasn't swept yet.
        //      Transient; the next tick clears them. Surface for operator visibility.
        for (final Map.Entry<String, DSLRuntimeState> entry : localByKey.entrySet()) {
            final DSLRuntimeState local = entry.getValue();
            if (!filter.isEmpty() && !filter.equals(local.getCatalog())) {
                continue;
            }
            final boolean isBundled = StaticRuleRegistry.active()
                .find(local.getCatalog(), local.getName())
                .isPresent();
            rows.add(isBundled ? renderBundledEntry(local) : renderOrphanEntry(local));
        }

        final JsonObject loaderStats = new JsonObject();
        loaderStats.addProperty("active", DSLClassLoaderManager.INSTANCE.activeCount());
        loaderStats.addProperty("pending", DSLClassLoaderManager.INSTANCE.pendingCount());
        final JsonObject envelope = new JsonObject();
        envelope.addProperty("generatedAt", System.currentTimeMillis());
        envelope.add("loaderStats", loaderStats);
        envelope.add("rules", rows);
        return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, GSON.toJson(envelope));
    }

    private static final Gson GSON = new Gson();

    /**
     * Single-rule fetch. Studio's catalog → row click and the editor both need the YAML
     * source, which {@code /list} intentionally omits. Lookup order:
     * <ol>
     *   <li>DAO row for {@code (catalog, name)} regardless of status — INACTIVE rules keep
     *       their content under the soft-pause contract so the editor can re-edit.</li>
     *   <li>{@link StaticRuleRegistry} fallback — bundled rules that have never been
     *       overridden by the operator. Returned with synthetic status {@code STATIC} and
     *       source {@code static}.</li>
     *   <li>Otherwise 404 {@code not_found}.</li>
     * </ol>
     *
     * <p>Default response is raw YAML ({@code Content-Type: application/x-yaml; charset=utf-8})
     * so a round-trip through {@code /addOrUpdate} is byte-exact. With {@code Accept:
     * application/json} the response is the envelope {@code {catalog, name, status, source,
     * contentHash, updateTime, content}} where {@code content} is a standard JSON-escaped
     * UTF-8 string (no base64). Either mode emits the same metadata as response headers
     * ({@code X-Sw-Content-Hash}, {@code X-Sw-Status}, {@code X-Sw-Source},
     * {@code X-Sw-Update-Time}) and an {@code ETag} based on the content hash, so an editor
     * reload with {@code If-None-Match} gets a cheap 304.
     *
     * <p>No cluster routing — reads are stateless and any node can serve from its local
     * DAO + {@link StaticRuleRegistry}.
     */
    public HttpResponse get(final Catalog catalog,
                            final String name,
                            final String source,
                            final String accept,
                            final String ifNoneMatch) {
        return doGet(catalog.getWireName(), name, source, accept, ifNoneMatch);
    }

    private HttpResponse doGet(final String catalog,
                                final String name,
                                final String source,
                                final String accept,
                                final String ifNoneMatch) {
        final HttpResponse validationError = validate(catalog, name);
        if (validationError != null) {
            return validationError;
        }
        final boolean forceBundled = "bundled".equalsIgnoreCase(source);
        if (!forceBundled && source != null && !source.isEmpty()
                && !"runtime".equalsIgnoreCase(source)) {
            return badRequest("invalid_source", catalog, name,
                "source must be 'runtime' (default) or 'bundled'");
        }

        final RuntimeRuleManagementDAO dao = resolveDao();
        if (dao == null && !forceBundled) {
            return serverError("dao_unavailable", catalog, name,
                "RuntimeRuleManagementDAO not resolvable — storage module may not be active");
        }

        // 1. DAO row — only when source != bundled. The bundled-source path is the operator's
        //    explicit "show me what's on disk" request and must NEVER fall through to the DAO,
        //    even when both copies exist.
        if (!forceBundled) {
            final RuntimeRuleManagementDAO.RuntimeRuleFile row;
            try {
                row = findRule(dao, catalog, name);
            } catch (final IOException ioe) {
                log.warn("runtime-rule /get: DAO lookup failed for {}/{}", catalog, name, ioe);
                return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE, MediaType.JSON_UTF_8,
                    jsonBody("storage_unavailable", catalog, name,
                        "DAO lookup failed: " + ioe.getMessage()));
            }
            if (row != null) {
                return renderGetResponse(catalog, name, row.getContent(), row.getStatus(),
                    "runtime", row.getUpdateTime(), accept, ifNoneMatch);
            }
        }

        // 2. Bundled (StaticRuleRegistry). Primary path when source=bundled, fallback otherwise.
        final String staticContent = StaticRuleRegistry.active().find(catalog, name).orElse(null);
        if (staticContent != null) {
            // updateTime=0 — capturing the actual file mtime would require threading it through
            // StaticRuleRegistry.record. Editor doesn't need precise; "0" is honest about it.
            return renderGetResponse(catalog, name, staticContent, "BUNDLED",
                "bundled", 0L, accept, ifNoneMatch);
        }

        // 3. 404 — message reflects which mode the operator asked for.
        return HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.JSON_UTF_8,
            jsonBody("not_found", catalog, name,
                forceBundled
                    ? "no bundled rule for this (catalog, name); source=bundled was requested"
                    : "no runtime rule and no bundled rule for this (catalog, name)"));
    }

    /**
     * Build the {@code GET /runtime/rule} response. Honours {@code Accept: application/json}
     * for the JSON envelope; defaults to raw YAML otherwise. Always emits the metadata
     * headers and {@code ETag} so the raw and JSON modes are equally introspectable.
     * Returns {@code 304 Not Modified} when the client's {@code If-None-Match} matches the
     * current content hash.
     */
    private static HttpResponse renderGetResponse(final String catalog, final String name,
                                                   final String content, final String status,
                                                   final String source, final long updateTime,
                                                   final String accept, final String ifNoneMatch) {
        final String contentHash = ContentHash.sha256Hex(content);
        final String eTag = "\"" + contentHash + "\"";
        if (eTag.equals(ifNoneMatch == null ? "" : ifNoneMatch.trim())) {
            return HttpResponse.of(
                ResponseHeaders.builder(HttpStatus.NOT_MODIFIED)
                    .add("X-Sw-Content-Hash", contentHash)
                    .add("X-Sw-Status", status)
                    .add("X-Sw-Source", source)
                    .add("X-Sw-Update-Time", Long.toString(updateTime))
                    .add(HttpHeaderNames.ETAG, eTag)
                    .build());
        }
        final boolean json = accept != null
            && accept.toLowerCase(Locale.ROOT).contains("application/json");
        if (json) {
            final JsonObject env = new JsonObject();
            env.addProperty("catalog", catalog);
            env.addProperty("name", name);
            env.addProperty("status", status);
            env.addProperty("source", source);
            env.addProperty("contentHash", contentHash);
            env.addProperty("updateTime", updateTime);
            env.addProperty("content", content);
            final String body = GSON.toJson(env);
            return HttpResponse.of(
                ResponseHeaders.builder(HttpStatus.OK)
                    .contentType(MediaType.JSON_UTF_8)
                    .add("X-Sw-Content-Hash", contentHash)
                    .add("X-Sw-Status", status)
                    .add("X-Sw-Source", source)
                    .add("X-Sw-Update-Time", Long.toString(updateTime))
                    .add(HttpHeaderNames.ETAG, eTag)
                    .build(),
                HttpData.ofUtf8(body));
        }
        return HttpResponse.of(
            ResponseHeaders.builder(HttpStatus.OK)
                .contentType(MediaType.create("application", "x-yaml").withCharset(StandardCharsets.UTF_8))
                .add("X-Sw-Content-Hash", contentHash)
                .add("X-Sw-Status", status)
                .add("X-Sw-Source", source)
                .add("X-Sw-Update-Time", Long.toString(updateTime))
                .add(HttpHeaderNames.ETAG, eTag)
                .build(),
            HttpData.ofUtf8(content == null ? "" : content));
    }

    /**
     * Read-only view of every static rule shipped with OAP for the given catalog. Studio's
     * catalogue browser merges this with {@code /list} (runtime overrides) for a unified
     * "available rules" view; the {@code overridden} flag on each entry tells the UI which
     * static rules currently have an operator override in place so they can be rendered
     * with the right state.
     *
     * <p>Always JSON: the body is an array of {@code {name, kind, contentHash, content?,
     * overridden}} objects. {@code content} is included by default and elided when
     * {@code withContent=false} so a catalogue browse can stay small (per-rule content can
     * then be fetched lazily via {@code GET /runtime/rule}).
     *
     * <p>Catalog scope: {@code otel-rules}, {@code log-mal-rules}, {@code telegraf-rules},
     * {@code lal} — the same
     * allowlist the write paths use. {@code .oal} files are not exposed here; they live
     * outside the runtime-rule plugin's scope today.
     */
    public HttpResponse listBundled(final Catalog catalog,
                                     final String withContentRaw) {
        return doListBundled(catalog.getWireName(), withContentRaw);
    }

    private HttpResponse doListBundled(final String catalog, final String withContentRaw) {
        final boolean withContent = parseFlag(withContentRaw)
            || "true".equalsIgnoreCase(withContentRaw == null ? "true" : withContentRaw.trim());
        // Cross-join with the DAO so each entry's `overridden` flag reflects current state.
        // Failure to read the DAO is non-fatal — we still return the bundled view; just mark
        // every entry overridden=false (best-effort) and log so operators can see the gap.
        final Set<String> overriddenNames = new HashSet<>();
        final RuntimeRuleManagementDAO dao = resolveDao();
        if (dao != null) {
            try {
                for (final RuntimeRuleManagementDAO.RuntimeRuleFile rule : dao.getAll()) {
                    if (catalog.equals(rule.getCatalog())) {
                        overriddenNames.add(rule.getName());
                    }
                }
            } catch (final IOException ioe) {
                log.warn("runtime-rule /bundled: DAO read failed for catalog={}; "
                    + "overridden flags will all be false this call", catalog, ioe);
            }
        }
        final List<StaticRuleRegistry.NamedRule> rules =
            StaticRuleRegistry.active().findByCatalog(catalog);
        final JsonArray out = new JsonArray();
        for (final StaticRuleRegistry.NamedRule rule : rules) {
            final JsonObject row = new JsonObject();
            row.addProperty("name", rule.getName());
            row.addProperty("kind", "bundled");
            row.addProperty("contentHash", ContentHash.sha256Hex(rule.getContent()));
            row.addProperty("overridden", overriddenNames.contains(rule.getName()));
            if (withContent) {
                row.addProperty("content", rule.getContent());
            }
            out.add(row);
        }
        return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, GSON.toJson(out));
    }

    public HttpResponse dump() {
        return doDump(null);
    }

    public HttpResponse dumpCatalog(final Catalog catalog) {
        return doDump(catalog.getWireName());
    }
    // ----- Shared handlers ---------------------------------------------------------------------

    private HttpResponse doAddOrUpdate(final String catalog, final String name, final HttpData body,
                                       final boolean allowStorageChange) {
        return doAddOrUpdate(catalog, name, body, allowStorageChange, false, false);
    }

    private HttpResponse doAddOrUpdate(final String catalog, final String name, final HttpData body,
                                       final boolean allowStorageChange, final boolean forceReapply) {
        return doAddOrUpdate(catalog, name, body, allowStorageChange, forceReapply, false);
    }

    /**
     * @param forceReapply when true, bypass the byte-identical no_change short-circuit so a
     *                     re-post of known-good content is not silently eaten. The request
     *                     enters the structural pipeline (Suspend broadcast + persist +
     *                     Resume), but if the engine sees no delta the apply itself is a
     *                     NO_CHANGE — the explicit Resume broadcast at commit-tail is what
     *                     unsticks peers that were left SUSPENDED by a prior failed push.
     *                     Set by {@code /addOrUpdate?force=true}; the default false keeps
     *                     CI idempotency working as designed.
     * @param forwarded    true when the request arrived via the cluster Forward RPC (one
     *                     of the {@code execute*} entry points); false for direct HTTP
     *                     callers.
     *                     Controls the routing path: direct callers forward to the main
     *                     when self isn't main; forwarded callers hit the fail-safe 421
     *                     instead of re-forwarding.
     */
    private HttpResponse doAddOrUpdate(final String catalog, final String name, final HttpData body,
                                       final boolean allowStorageChange, final boolean forceReapply,
                                       final boolean forwarded) {
        final HttpResponse validationError = validate(catalog, name);
        if (validationError != null) {
            return validationError;
        }
        final String content = body == null ? "" : body.toStringUtf8();
        if (content.isEmpty()) {
            return badRequest("empty_body", catalog, name, "request body must be the raw rule content");
        }
        // Single-main routing. Self is main → null → run local workflow. Non-main + not
        // forwarded → forward to main and relay response. Non-main + forwarded → fail-safe
        // 421 (cluster view split; refuse to re-forward).
        //
        // The operation string MUST match exactly one of the cases the cluster receiver's
        // switch in RuntimeRuleClusterServiceImpl handles ("addOrUpdate", "inactivate",
        // "delete"); anything else returns 400 forward_unknown_operation. The forceReapply
        // flag rides on the protobuf body's own field, not in the operation string — early
        // versions encoded it as "addOrUpdate?force=true" which the receiver never decoded.
        final HttpResponse routed = routeOrNull(catalog, name,
            "addOrUpdate",
            content.getBytes(StandardCharsets.UTF_8),
            allowStorageChange, forceReapply, forwarded);
        if (routed != null) {
            return routed;
        }

        // Hold the per-file lock across the ENTIRE workflow (prior-file lookup, classify,
        // guardrail, Suspend, apply, persist, finalize/discard, Resume). The lock is
        // reentrant, so the dslManager's internal acquires nest safely. This serializes
        // concurrent REST requests for the same (catalog, name) on this OAP — otherwise the
        // pendingCommits stash between apply and persist could be overwritten by a racing
        // second request, and the first request's finalize would drain the wrong content.
        // Different files do not contend (per-file lock cache).
        //
        // Uses LockMetrics.acquireForRest which wraps tryLock with:
        //   - bounded timeout (REST_LOCK_TIMEOUT_MS) — returns false on timeout instead of
        //     parking the Armeria thread for an unbounded time
        //   - runtime_rule_lock_wait_seconds histogram (path=rest) for every attempt
        //   - runtime_rule_lock_contention_total counter (path=rest,outcome=timeout) on false
        //   - WARN log line when an acquire took > 1s (catches pathological waits even
        //     without operators looking at the dashboard)
        final ReentrantLock perFile = AppliedRuleScript.lockFor(dslManager.getRules(), catalog, name);
        if (!dslManager.getLockMetrics().acquireForRest(perFile, REST_LOCK_TIMEOUT_MS, catalog, name)) {
            return HttpResponse.of(HttpStatus.CONFLICT, MediaType.JSON_UTF_8,
                jsonBody("update_in_progress", catalog, name,
                    "another update for this rule file is in progress on this OAP; retry"));
        }
        try (HistogramMetrics.Timer ignored =
                 dslManager.getLockMetrics().startRestHoldTimer()) {
            return doAddOrUpdateLocked(catalog, name, content, allowStorageChange, forceReapply);
        } finally {
            perFile.unlock();
        }
    }

    /** Full workflow with the per-file lock held. See {@link #doAddOrUpdate} for rationale. */
    private HttpResponse doAddOrUpdateLocked(final String catalog, final String name,
                                              final String content,
                                              final boolean allowStorageChange,
                                              final boolean forceReapply) {
        // Full prior-file lookup (not just content): the no_change short-circuit must
        // distinguish ACTIVE-same-content (true no-op) from INACTIVE-same-content
        // (reactivation request — must persist + apply so the handlers come back). Feeds
        // the compile_failed check, the no_change short-circuit, and the allowStorageChange
        // guardrail. Lookup failure is surfaced as 503 — silently treating it as "no prior
        // row" would let storageChangeGuardrail wave a destructive STRUCTURAL change through
        // because priorContent==null reads as first-time create.
        final RuntimeRuleManagementDAO.RuntimeRuleFile priorRuleFile;
        try {
            priorRuleFile = currentRuleFile(catalog, name);
        } catch (final IOException ioe) {
            log.warn("runtime-rule: prior-row lookup failed for {}/{}", catalog, name, ioe);
            return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE, MediaType.JSON_UTF_8,
                jsonBody("storage_unavailable", catalog, name,
                    "prior-row lookup failed: " + ioe.getMessage()));
        }
        // When there is no DB row yet, fall back to the static content captured by the
        // runtime-rule extension at boot. Without this fallback the delta classifier would see
        // null and treat the first /addOrUpdate against a shipped static rule as "new rule" —
        // masking shape-breaking edits the storage-change guardrail should have caught.
        final String priorContent = priorRuleFile != null
            ? priorRuleFile.getContent()
            : StaticRuleRegistry.active().find(catalog, name).orElse(null);
        // A static-only rule (no DB row but static content exists) is implicitly ACTIVE —
        // RuleSetMerger recorded the on-disk bytes at boot via StaticRuleRegistry. Treat
        // it as ACTIVE for the no_change short-circuit so a re-post of the static bytes is
        // a cheap no-op.
        final boolean priorActive = priorRuleFile != null
            ? !RuntimeRule.STATUS_INACTIVE.equals(priorRuleFile.getStatus())
            : priorContent != null;

        // Byte-identical short-circuit. Only fires for ACTIVE-and-same-content AND when the
        // caller didn't force a re-apply. A re-post of the same bytes on an INACTIVE row is
        // an explicit reactivation and must run through the full apply pipeline.
        // /addOrUpdate?force=true sets forceReapply=true so a same-content recovery push
        // isn't silently eaten.
        if (!forceReapply
                && priorActive
                && priorContent != null
                && priorContent.equals(content)) {
            return ok(HttpStatus.OK, "no_change", catalog, name,
                "content byte-identical to current ACTIVE row; no-op");
        }

        // Classify the delta to emit the right response shape and to drive the guardrail
        // below. Parse failures (malformed YAML on the new side, or a MAL expression that
        // can't even AST-parse) surface as 400 compile_failed. The classifier is cheap
        // (AST walk, no Javassist codegen) so doing this synchronously on the HTTP thread
        // is fine.
        final DSLDelta delta;
        try {
            // Engine-driven classification — routes via RuleEngineRegistry so a catalog
            // declared on MalRuleEngine.supportedCatalogs (e.g., telegraf-rules) classifies
            // as MAL automatically, no parallel string list to maintain.
            delta = DSLScriptKey.isMalCatalog(dslManager.getEngineRegistry(), catalog)
                ? DeltaClassifier.classifyMal(priorContent, content)
                : DeltaClassifier.classifyLal(priorContent, content);
        } catch (final RuntimeException pe) {
            log.warn("runtime-rule: compile_failed during classify for {}/{}: {}",
                catalog, name, pe.getMessage());
            return badRequest("compile_failed", catalog, name, pe.getMessage());
        }

        // Destructive-edit guardrail fires BEFORE any Suspend / persist work — rejected
        // requests must not drain peers or touch the row. Narrow by design: only MAL
        // shape-break (scope type or explicit downsampling moved) and LAL outputType /
        // rule-key changes. FILTER_ONLY body tweaks never trigger.
        final String storageChangeRejection = storageChangeGuardrail(
            catalog, name, priorContent, content, allowStorageChange);
        if (storageChangeRejection != null) {
            return HttpResponse.of(HttpStatus.CONFLICT, MediaType.JSON_UTF_8,
                jsonBody("storage_change_requires_explicit_approval", catalog, name,
                    storageChangeRejection));
        }

        // FILTER_ONLY fast path: pure local body/filter swap. No Suspend broadcast, no DDL,
        // no alarm reset. Apply locally; on success, persist; on failure, return 500 without
        // persist. Peers observe the row on their next tick and run their own fast path.
        if (delta.classification() == Classification.FILTER_ONLY) {
            return applyFilterOnly(catalog, name, content, delta);
        }

        // STRUCTURAL / NEW path. Order:
        //  1. local self-suspend (park entry dispatch for the prior metrics; SELF origin)
        //  2. peer Suspend broadcast (bounded per-peer deadline; unreachable peers self-heal)
        //  3. local apply — compile, register, DDL through CreatingListeners, isExists verify
        //  4. If apply fails → local Resume + broadcast Resume + return 500 without persist.
        //     Peers flip back to RUNNING within an RPC round-trip.
        //  5. If apply succeeds → persist. On persist success: finalize commit. On persist
        //     failure: discard commit + broadcast Resume + return 500.
        return applyStructural(catalog, name, content, delta);
    }

    /**
     * Fast-path apply for body/filter edits that do not move metric shape. Persist the row
     * first — the design's commit point — then swap the compiled body in locally. No
     * Suspend broadcast is sent because no storage identity is moving.
     *
     * <p>Persist-first preserves the persist-as-commit invariant: if the DB write fails, no
     * local state advances and the operator's 500 {@code persist_failed} response is
     * honest. Previously this path applied locally first and, on persist failure, returned
     * 500 while the local node kept serving the new bundle — a one-node divergence window
     * that closed only on the next dslManager tick replaying the old DB content. FILTER_ONLY
     * has no DDL to roll back, so the worst case after "persist succeeded, local apply
     * failed" is a brief local-old-vs-DB-new gap that the next tick converges (same
     * semantics peers already observe when they catch up by tick). STRUCTURAL still
     * apply-first + stash-and-commit because its DDL cannot be undone by a simple row
     * revert.
     */
    private HttpResponse applyFilterOnly(final String catalog, final String name,
                                          final String content, final DSLDelta delta) {
        final long updateTime = System.currentTimeMillis();
        // 1. Persist first — commit point. Nothing local has changed yet; a persist failure
        //    here leaves the node serving the pre-edit bundle, which matches the response.
        final HttpResponse persistError = persistRuleSync(catalog, name, content, updateTime);
        if (persistError != null) {
            return persistError;
        }
        // 2. Apply locally. An unexpected compile/register failure after persist would leave
        //    the DB row ahead of local state for up to one tick interval; the next tick
        //    re-reads the DB and retries the apply. Peers already converge via the same
        //    tick-driven path (FILTER_ONLY never broadcasts), so this failure mode is
        //    indistinguishable from the existing "peer catches up on its next tick" path —
        //    no new divergence semantics to document.
        final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile = new RuntimeRuleManagementDAO.RuntimeRuleFile(
            catalog, name, content, RuntimeRule.STATUS_ACTIVE, updateTime);
        final DSLRuntimeState postApply;
        try {
            postApply = dslManager.applyNowForRuleFile(ruleFile);
        } catch (final Throwable t) {
            log.error("runtime-rule FILTER_ONLY apply failed after persist for {}/{} — DB "
                + "reflects the new content; this node will converge on the next dslManager "
                + "tick (same path peers use).", catalog, name, t);
            return ok(HttpStatus.OK, "filter_only_persisted", catalog, name,
                "row persisted; local apply deferred to next tick: " + t.getMessage());
        }
        if (postApply != null && postApply.getLastApplyError() != null) {
            log.warn("runtime-rule FILTER_ONLY apply recorded an error after persist for "
                + "{}/{}: {}. Next tick will retry.",
                catalog, name, postApply.getLastApplyError());
            return ok(HttpStatus.OK, "filter_only_persisted", catalog, name,
                "row persisted; local apply deferred to next tick: "
                    + postApply.getLastApplyError());
        }
        return ok(HttpStatus.OK, "filter_only_applied", catalog, name,
            "body/filter edits applied; no DDL, no alarm reset");
    }

    /**
     * STRUCTURAL / NEW apply: local Suspend → peer Suspend broadcast → local compile + DDL +
     * verify → (on success) persist + resume; (on failure) local resume without persist.
     * The in-memory state machine is the source of truth during the apply window; the row
     * write is the commit point that lets peers converge.
     */
    private HttpResponse applyStructural(final String catalog, final String name,
                                          final String content, final DSLDelta delta) {
        // Local self-suspend first so this node stops serving the old bundle before anyone
        // else learns of the new content. The suspend records SuspendOrigin.SELF so a racing
        // peer Suspend (should not happen under correct single-main routing) is rejected with
        // HTTP 409 rather than merged into BOTH.
        final SuspendResult local = dslManager.getSuspendCoord().localSuspend(catalog, name);
        if (local == SuspendResult.REJECTED_ORIGIN_CONFLICT) {
            // Another OAP thinks it's the main for this file. Reject the operator's request;
            // correct routing never hits this branch. Do NOT broadcast Suspend (peer state
            // already reflects the other main's activity).
            return HttpResponse.of(HttpStatus.CONFLICT, MediaType.JSON_UTF_8,
                jsonBody("origin_conflict", catalog, name,
                    "peer origin already holds this bundle — cluster routing misfire or "
                        + "split-brain; refusing to run a second main-node apply concurrently"));
        }
        log.info("runtime-rule STRUCTURAL apply for {}/{}: local suspend result = {}",
            catalog, name, local);

        // Peer broadcast. Bounded deadline per peer; unreachable peers recover via the
        // dslManager's self-heal sweep when Resume is later broadcast or after
        // selfHealThresholdMs if the main crashes before sending Resume. Inspect the acks for
        // REJECTED — a peer rejects Suspend when IT holds SELF origin (mid-apply), which
        // means two OAPs both think they are the main for this file. Abort here rather than
        // double-applying; the local self-suspend is reverted and the caller gets a 409.
        final List<SuspendAck> suspendAcks = broadcastSuspend(catalog, name, "addOrUpdate");
        final SuspendAck rejected = firstRejected(suspendAcks);
        if (rejected != null) {
            dslManager.getSuspendCoord().localResume(catalog, name);
            // Resume the peers that DID accept (SUSPENDED / ALREADY_SUSPENDED entries) so they
            // flip back to RUNNING within one RPC round-trip. The rejecting peer ignores the
            // Resume because it never transitioned to PEER-suspend under our sender id.
            broadcastResume(catalog, name, "split_brain_detected");
            log.error("runtime-rule STRUCTURAL apply ABORTED for {}/{} — peer {} already "
                + "holds SELF origin: {}. Cluster routing misfire; refusing to double-apply.",
                catalog, name, rejected.getNodeId(), rejected.getDetail());
            return HttpResponse.of(HttpStatus.CONFLICT, MediaType.JSON_UTF_8,
                jsonBody("split_brain_detected", catalog, name,
                    "peer " + rejected.getNodeId() + " reports a concurrent apply in flight "
                        + "(origin conflict); only one main per (catalog, name) is permitted. "
                        + "Re-run once cluster membership stabilizes."));
        }

        // Try the local apply with deferCommit=true. applyNowForRuleFile internally calls
        // MalFileApplier.apply which runs the CreatingListener chain (DDL + isExists verify
        // in the dslManager's verifyPostApply). Verify failure lands in
        // DSLRuntimeState.lastApplyError. On success the commit's destructive tail (drop
        // removedMetrics, swap appliedMal/appliedContent, retire old loader, alarm reset,
        // advance snapshot) is stashed in the dslManager — we drain it below once persist
        // resolves.
        final long updateTime = System.currentTimeMillis();
        final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile = new RuntimeRuleManagementDAO.RuntimeRuleFile(
            catalog, name, content, RuntimeRule.STATUS_ACTIVE, updateTime);
        final DSLRuntimeState postApply;
        try {
            postApply = dslManager.applyNowForRuleFile(ruleFile, true);
        } catch (final Throwable t) {
            log.error("runtime-rule STRUCTURAL apply threw for {}/{}", catalog, name, t);
            dslManager.getSuspendCoord().localResume(catalog, name);
            // Peers went SUSPENDED on our earlier broadcast; let them know the apply
            // aborted so they flip back to RUNNING within an RPC round-trip.
            broadcastResume(catalog, name, "apply_threw");
            return serverError("apply_failed", catalog, name, t.getMessage());
        }
        if (postApply != null && postApply.getLastApplyError() != null) {
            // Apply failed (DDL verify mismatch, compile surprise, applier exception). Row
            // is NOT yet persisted. applyOneRuleFile already rolled back its own partial
            // registration on the exception path; the pendingCommits stash is only
            // populated after verifyPostApply passes, so no pending drain to do here.
            // Resume the retained pre-suspend bundle locally so this node goes back to
            // serving samples, then broadcast Resume so peers recover immediately instead
            // of waiting on the 60 s self-heal window.
            dslManager.getSuspendCoord().localResume(catalog, name);
            broadcastResume(catalog, name, "apply_failed");
            final String err = postApply.getLastApplyError();
            if (err.contains("isExists verify FAILED")
                    || err.contains("ddl")
                    || err.contains("install")) {
                return serverError("ddl_verify_failed", catalog, name, err);
            }
            // Cross-file ownership conflict — the operator's rule names a metric
            // already claimed by another active file. Operator-fixable, not a server
            // error: surface as 409 so callers (and the e2e) can treat it the same as
            // the other apply rejections (allowStorageChange, /delete-on-ACTIVE, ...).
            if (err.contains("rule-name collision")) {
                return HttpResponse.of(HttpStatus.CONFLICT, MediaType.JSON_UTF_8,
                    jsonBody("ownership_conflict", catalog, name, err));
            }
            return serverError("apply_failed", catalog, name, err);
        }

        // Apply succeeded + verified. Commit the row — the design's commit point. Retry a
        // couple of times on transient failures before giving up; the per-backend
        // RuntimeRuleManagementDAO.save can throw on a brief storage outage. A narrow retry
        // here avoids turning a blip into a cluster-divergence event.
        HttpResponse persistError = persistRuleSync(catalog, name, content, updateTime);
        if (persistError != null) {
            try {
                Thread.sleep(100L);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            persistError = persistRuleSync(catalog, name, content, updateTime);
        }
        if (persistError != null) {
            // Persist still failing. The local node has registered added + shape-break
            // metrics in MeterSystem (DDL fired, isExists verified) while the DB and peers
            // remain on the old content. Discard drains the pending commit by removing only
            // the added + shape-break metrics — it does NOT drop removedMetrics (the commit
            // was stashed before that step, so those are still alive) and does NOT swap
            // appliedMal/appliedContent (still on the pre-apply bundle). Net outcome:
            // local node converges back to the pre-apply bundle exactly, no divergence from
            // what the DB still says is current.
            try {
                dslManager.getCommitCoord().discardCommit(catalog, name);
            } catch (final Throwable rt) {
                log.error("runtime-rule CRITICAL: persist-failure discard itself failed for "
                    + "{}/{}; state is inconsistent and requires operator intervention",
                    catalog, name, rt);
            }
            // Peers are still SUSPENDED on our earlier broadcast. The DB didn't advance,
            // so self-heal would eventually flip them back, but broadcasting Resume now
            // cuts the dispatch gap from 60 s to a single RPC round-trip.
            broadcastResume(catalog, name, "persist_failed");
            log.error("runtime-rule CRITICAL: STRUCTURAL persist FAILED after successful apply "
                + "for {}/{} — discarded pending commit; local node re-aligned with old "
                + "content. Operator action: re-push via /addOrUpdate once storage is healthy.",
                catalog, name);
            return persistError;
        }

        // Persist succeeded — drain the pending commit now that the DB reflects the new
        // content. commitCoord.finalizeCommit drops removedMetrics, swaps the applied
        // pointers, retires the old loader, fires alarm reset, and advances the snapshot.
        //
        // Commit-tail failure handling: the DB row is durable (persist already succeeded),
        // so peers converge from the DB — but on THIS node the local drop+recreate may
        // not have fully landed. Return 500 commit_deferred so the operator sees a clear
        // "DB row flipped, local commit threw" signal and can retry. Returning 200 would
        // tell the operator "done" while the backend schema on this node may still be
        // stale — that's the failure mode the review flagged.
        Throwable commitFailure = null;
        boolean drained = false;
        try {
            drained = dslManager.getCommitCoord().finalizeCommit(catalog, name);
        } catch (final Throwable t) {
            commitFailure = t;
            log.error("runtime-rule CRITICAL: finalize commit FAILED for {}/{} after persist "
                + "succeeded — DB is authoritative, peers will converge. Operator action: "
                + "inspect log for the underlying cause.", catalog, name, t);
        }
        if (commitFailure != null) {
            return serverError("commit_deferred", catalog, name,
                "DB row persisted, but local commit-tail threw — backend shape on this "
                    + "node may not have fully landed. Peers converge from DB; this node "
                    + "will retry on the next dslManager tick. Cause: "
                    + commitFailure.getMessage());
        }

        // No commit was drained — typical for {@code force=true} re-applies on byte-
        // identical content (engine returned NO_CHANGE so nothing was stashed). Peers are
        // still PEER-suspended from our earlier broadcast and would only converge via the
        // 60 s self-heal window without an explicit Resume. Send the Resume now so peers
        // recover within an RPC round-trip.
        if (!drained) {
            broadcastResume(catalog, name, "force_no_change");
        }

        return ok(HttpStatus.OK, "structural_applied", catalog, name,
            "structural apply succeeded" + describeDelta(delta));
    }

    /**
     * Write the row through {@link RuntimeRuleManagementDAO#save} so a DAO failure is surfaced
     * to the caller instead of silently swallowed. The earlier ManagementStreamProcessor path
     * routed through the generic {@code IManagementDAO.insert}, which BanyanDB never persisted
     * (just logged) and ES/JDBC short-circuited on duplicate row — both broke the
     * persist-is-commit invariant for {@code /addOrUpdate} updates and {@code /inactivate}
     * status flips. The DAO contract is now an explicit upsert per backend.
     *
     * @return {@code null} when the row is durable in storage; a 500 {@link HttpResponse}
     *         otherwise. Callers chain on null so the happy-path stays readable.
     */
    private HttpResponse persistRuleSync(final String catalog, final String name,
                                         final String content, final long updateTime) {
        final RuntimeRuleManagementDAO dao = resolveDao();
        if (dao == null) {
            return serverError("persist_failed", catalog, name,
                "RuntimeRuleManagementDAO unavailable");
        }
        final RuntimeRule rule = new RuntimeRule();
        rule.setCatalog(catalog);
        rule.setName(name);
        rule.setContent(content);
        rule.setStatus(RuntimeRule.STATUS_ACTIVE);
        rule.setUpdateTime(updateTime);
        try {
            dao.save(rule);
            return null;
        } catch (final Throwable t) {
            log.error("failed to persist runtime rule {}/{}", catalog, name, t);
            return serverError("persist_failed", catalog, name, t.getMessage());
        }
    }

    /**
     * Degraded-mode fallback used when the dslManager isn't wired (early boot, embedded test
     * topologies). Persist the row so storage is durable and the dslManager can catch up on
     * its own tick when it comes online; return 202.
     */
    private HttpResponse persistRowAndReturnPending(final String catalog, final String name,
                                                     final String content, final DSLDelta delta) {
        final long updateTime = System.currentTimeMillis();
        final HttpResponse persistError = persistRuleSync(catalog, name, content, updateTime);
        if (persistError != null) {
            return persistError;
        }
        return ok(HttpStatus.ACCEPTED, "persisted_apply_pending", catalog, name,
            "row written; classification=" + delta.classification().name()
                + "; dslManager will apply within the tick interval");
    }

    private static String describeDelta(final DSLDelta delta) {
        final StringBuilder sb = new StringBuilder();
        if (!delta.addedMetrics().isEmpty()) {
            sb.append("; added=").append(delta.addedMetrics().size());
        }
        if (!delta.removedMetrics().isEmpty()) {
            sb.append("; removed=").append(delta.removedMetrics().size());
        }
        if (!delta.shapeBreakMetrics().isEmpty()) {
            sb.append("; shape-break=").append(delta.shapeBreakMetrics().size());
        }
        return sb.toString();
    }

    /**
     * Returns a rejection message when the edit is storage-affecting and the guardrail flag
     * is not set; null when the edit is safe or the flag permits it. "Storage-affecting" is
     * narrow by design: only shape-breaking MAL edits (scope type or explicit downsampling
     * moved), or LAL edits that change a rule's outputType / add-remove rule keys. FILTER_ONLY
     * body tweaks never trigger the guardrail — those don't touch storage.
     */
    private String storageChangeGuardrail(final String catalog, final String name,
                                          final String priorContent, final String newContent,
                                          final boolean allowStorageChange) {
        if (allowStorageChange) {
            return null;
        }
        final org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngine<?> engine =
            dslManager.getEngineRegistry().forCatalog(catalog);
        if (engine == null) {
            return null;
        }
        final Set<String> storageAffected;
        try {
            storageAffected = engine.storageImpactKeys(priorContent, newContent);
        } catch (final RuntimeException e) {
            return "classify failed (cannot evaluate storage impact): " + e.getMessage();
        }
        if (storageAffected.isEmpty()) {
            return null;
        }
        return "update would trigger a storage-level change for " + catalog + "/" + name
            + " affecting " + storageAffected
            + "; retry with allowStorageChange=true to accept data loss (measure drop + "
            + "downsampling re-class on BanyanDB, orphaned rows on JDBC/ES)";
    }

    /**
     * Full prior-row lookup. Returns null when the DAO is unavailable (early boot, some
     * embedded test topologies) or when no row exists for {@code (catalog, name)}. The
     * caller reads the row's content + status fields: the no_change short-circuit needs
     * status to distinguish ACTIVE from INACTIVE (a re-post of the same content on an
     * INACTIVE row reactivates rather than becoming a no-op), and the delta classifier
     * reads content directly. Uses {@link RuntimeRuleManagementDAO#getAll()} + in-memory
     * filter because the rule count is small (dozens per cluster in practice) and adding
     * a per-row getter would be a cross-module API change for a handful of callers.
     *
     * <p>Storage read failures propagate to the caller as {@link IOException} — they MUST
     * NOT be silently swallowed and surfaced as "no prior row". The
     * {@link #storageChangeGuardrail} treats a null priorContent as a first-time create and
     * skips the check; if a transient DAO blip turned a real STRUCTURAL update into an
     * apparent first-time create, the guardrail would let a destructive change through.
     * Callers translate the IOException into a 503 so the operator can retry.
     */
    private RuntimeRuleManagementDAO.RuntimeRuleFile currentRuleFile(final String catalog, final String name)
            throws IOException {
        final RuntimeRuleManagementDAO dao = resolveDao();
        if (dao == null) {
            return null;
        }
        for (final RuntimeRuleManagementDAO.RuntimeRuleFile r : dao.getAll()) {
            if (catalog.equals(r.getCatalog()) && name.equals(r.getName())) {
                return r;
            }
        }
        return null;
    }

    /**
     * Accept common truthy forms: "true"/"1"/"yes" (case-insensitive) → true. Everything else,
     * including null and empty → false. Query-string bool is notoriously inconsistent across
     * clients (curl, browsers, scripts), so we normalize here rather than trusting any single
     * form.
     */
    private static boolean parseFlag(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        final String v = raw.trim().toLowerCase();
        return "true".equals(v) || "1".equals(v) || "yes".equals(v);
    }

    /**
     * Fire Suspend to every non-self peer on the OAP cluster bus. Returns the aggregated ack
     * list — null entries are unreachable peers. Unreachable peers log and self-heal via the
     * dslManager's 60s rule. The caller inspects the list for {@link SuspendState#REJECTED}
     * entries before proceeding: a REJECTED ack means another OAP is concurrently mid-apply
     * for the same (catalog, name) under its own SELF origin (routing misfire / split-brain).
     * Ignoring it would let both OAPs apply-and-persist for the same file.
     */
    private List<SuspendAck> broadcastSuspend(final String catalog, final String name, final String reason) {
        if (clusterClient == null) {
            return Collections.emptyList(); // Not expected in production; guard for tests.
        }
        try {
            return clusterClient.broadcastSuspend(catalog, name, reason);
        } catch (final Throwable t) {
            log.warn("runtime-rule Suspend broadcast failed for {}/{}; peers will self-heal "
                + "via dslManager next tick", catalog, name, t);
            return Collections.emptyList();
        }
    }

    /**
     * Inspect Suspend acks for the split-brain guard: if any peer responded with REJECTED
     * (origin conflict — it believes it is the main and is mid-apply), surface that to the
     * caller so we can abort before persisting. Unreachable peers (null entries) are ignored
     * here — they recover via self-heal. Returns null when no peer rejected.
     */
    private static SuspendAck firstRejected(final List<SuspendAck> acks) {
        if (acks == null) {
            return null;
        }
        for (final SuspendAck ack : acks) {
            if (ack != null && ack.getState() == SuspendState.REJECTED) {
                return ack;
            }
        }
        return null;
    }

    /**
     * Fire Resume to every non-self peer. Called on every failure branch of the main-node's
     * STRUCTURAL apply so peers flip back to RUNNING within an RPC round-trip instead of
     * waiting for the 60 s self-heal threshold. In the 99% case (compile / verify / persist
     * fails on the main and the main is alive to broadcast), peers resume immediately. In the
     * 1% case (main crashes between Suspend and Resume), self-heal remains the backstop.
     */
    private void broadcastResume(final String catalog, final String name, final String reason) {
        if (clusterClient == null) {
            return;
        }
        try {
            clusterClient.broadcastResume(catalog, name, reason);
        } catch (final Throwable t) {
            log.warn("runtime-rule Resume broadcast failed for {}/{} (reason={}); peers will "
                + "self-heal via dslManager after selfHealThresholdMs",
                catalog, name, reason, t);
        }
    }

    private HttpResponse doInactivate(final String catalog, final String name) {
        return doInactivate(catalog, name, false);
    }

    private HttpResponse doInactivate(final String catalog, final String name,
                                       final boolean forwarded) {
        final HttpResponse validationError = validate(catalog, name);
        if (validationError != null) {
            return validationError;
        }
        final HttpResponse routed = routeOrNull(catalog, name, "inactivate",
            new byte[0], false, false, forwarded);
        if (routed != null) {
            return routed;
        }
        final RuntimeRuleManagementDAO dao = resolveDao();
        if (dao == null) {
            return serverError("dao_unavailable", catalog, name,
                "RuntimeRuleManagementDAO not resolvable — storage module may not be active");
        }
        // Hold the same per-file lock /addOrUpdate holds so the Suspend → row flip → (peer)
        // tick pipeline serializes with a racing /addOrUpdate on the same file. Without this
        // a concurrent update could land its pending commit between our broadcastSuspend and
        // the status UPSERT, producing a bundle that's live-with-content on peers and INACTIVE
        // in the DB.
        final ReentrantLock perFile = AppliedRuleScript.lockFor(dslManager.getRules(), catalog, name);
        if (!dslManager.getLockMetrics().acquireForRest(perFile, REST_LOCK_TIMEOUT_MS, catalog, name)) {
            return HttpResponse.of(HttpStatus.CONFLICT, MediaType.JSON_UTF_8,
                jsonBody("update_in_progress", catalog, name,
                    "another update for this rule file is in progress on this OAP; retry"));
        }
        try (HistogramMetrics.Timer ignored =
                 dslManager.getLockMetrics().startRestHoldTimer()) {
            return doInactivateLocked(catalog, name, dao);
        } finally {
            perFile.unlock();
        }
    }

    private HttpResponse doInactivateLocked(final String catalog, final String name,
                                             final RuntimeRuleManagementDAO dao) {
        final RuntimeRuleManagementDAO.RuntimeRuleFile existing;
        try {
            existing = findRuleFile(dao, catalog, name);
        } catch (final IOException ioe) {
            log.error("failed to look up runtime rule {}/{} for inactivate", catalog, name, ioe);
            return serverError("inactivate_failed", catalog, name, ioe.getMessage());
        }
        if (existing == null) {
            // No DB row — fall back to the static content captured by the runtime-rule
            // extension at boot. If a static version of this rule exists on disk, the
            // operator is asking to silence it: persist an INACTIVE tombstone carrying the
            // static content (so /dump and re-activation both have the authoritative body)
            // and proceed with the destructive pipeline below. If neither row nor static
            // exists, there is genuinely nothing to inactivate.
            final String staticContent = StaticRuleRegistry.active().find(catalog, name).orElse(null);
            if (staticContent == null) {
                return ok(HttpStatus.OK, "not_found", catalog, name,
                    "no runtime-rule row and no static version on disk; nothing to inactivate");
            }
            return doInactivateStaticTombstone(catalog, name, staticContent);
        }
        if (RuntimeRule.STATUS_INACTIVE.equals(existing.getStatus())) {
            return ok(HttpStatus.OK, "already_inactive", catalog, name,
                "rule is already INACTIVE");
        }
        return runInactivePipeline(catalog, name, existing.getContent(), false);
    }

    /**
     * Insert an {@code INACTIVE} tombstone row carrying the static content and drive the
     * same destructive pipeline an existing-row /inactivate would run. Used when an operator
     * inactivates a rule that only exists on disk (static file, no DB row yet) — the
     * tombstone row becomes the source of truth so a reboot skips the static load and every
     * peer converges on "not running" via the dslManager tick.
     */
    private HttpResponse doInactivateStaticTombstone(final String catalog, final String name,
                                                      final String staticContent) {
        return runInactivePipeline(catalog, name, staticContent, true);
    }

    /**
     * Shared pipeline for {@code /inactivate}:
     * <ol>
     *   <li>Local self-suspend — main stops dispatching before peers learn of the removal.</li>
     *   <li>Broadcast {@code Suspend} — peers park dispatch; origin conflict → abort with
     *       {@code Resume} + 409.</li>
     *   <li>Persist {@code INACTIVE} row synchronously; failure → {@code Resume} + 500
     *       (rollback point, cluster state never diverges).</li>
     *   <li>Drive local teardown inline so main doesn't keep serving the bundle for up to
     *       one tick interval after the status flip.</li>
     * </ol>
     *
     * @param staticTombstone {@code true} when the row didn't previously exist and we're
     *                        creating it from static content; changes the applyStatus string
     *                        returned to the operator so /list + dashboards can distinguish
     *                        the two cases.
     */
    private HttpResponse runInactivePipeline(final String catalog, final String name,
                                              final String content, final boolean staticTombstone) {
        // Local self-suspend first so main stops serving the old bundle before the peer
        // broadcast. Without this the main kept serving while peers were told to Suspend —
        // peers stop first, cluster state diverges until the main's next tick drives teardown.
        final SuspendResult local = dslManager.getSuspendCoord().localSuspend(catalog, name);
        if (local == SuspendResult.REJECTED_ORIGIN_CONFLICT) {
            return HttpResponse.of(HttpStatus.CONFLICT, MediaType.JSON_UTF_8,
                jsonBody("origin_conflict", catalog, name,
                    "peer origin already holds this bundle — cluster routing misfire; "
                        + "refusing to inactivate while a peer reports concurrent apply"));
        }
        // Inactivate removes every metric the bundle owned across the cluster. Suspend peer
        // dispatch before the status flip propagates so samples arriving between the UPSERT
        // and the peer dslManager tick don't land in the soon-to-be-dropped bundle.
        final List<SuspendAck> suspendAcks = broadcastSuspend(catalog, name, "inactivate");
        final SuspendAck rejected = firstRejected(suspendAcks);
        if (rejected != null) {
            dslManager.getSuspendCoord().localResume(catalog, name);
            broadcastResume(catalog, name, "split_brain_detected");
            log.error("runtime-rule inactivate ABORTED for {}/{} — peer {} already holds "
                + "SELF origin: {}", catalog, name, rejected.getNodeId(), rejected.getDetail());
            return HttpResponse.of(HttpStatus.CONFLICT, MediaType.JSON_UTF_8,
                jsonBody("split_brain_detected", catalog, name,
                    "peer " + rejected.getNodeId() + " reports a concurrent apply in flight"));
        }

        final RuntimeRuleManagementDAO inactivateDao = resolveDao();
        if (inactivateDao == null) {
            dslManager.getSuspendCoord().localResume(catalog, name);
            broadcastResume(catalog, name, "inactivate_persist_failed");
            return serverError("inactivate_failed", catalog, name,
                "RuntimeRuleManagementDAO unavailable");
        }
        final RuntimeRule rule = new RuntimeRule();
        rule.setCatalog(catalog);
        rule.setName(name);
        rule.setContent(content);
        rule.setStatus(RuntimeRule.STATUS_INACTIVE);
        rule.setUpdateTime(System.currentTimeMillis());
        try {
            inactivateDao.save(rule);
        } catch (final Throwable t) {
            // Suspend is already in-flight; if we don't Resume, peers sit suspended for
            // selfHealThresholdMs. Send Resume now so they recover within one RPC round-trip.
            dslManager.getSuspendCoord().localResume(catalog, name);
            broadcastResume(catalog, name, "inactivate_persist_failed");
            log.error("failed to inactivate runtime rule {}/{}", catalog, name, t);
            return serverError("inactivate_failed", catalog, name, t.getMessage());
        }

        // Drive local teardown immediately now that the DB row reflects INACTIVE — main owns
        // the write, so the dslManager's tick would eventually do this, but waiting means the
        // main keeps serving the removed bundle for up to one tick interval (30 s by default).
        // applyNowForRuleFile is idempotent; if the tick fires first, the second call is a
        // fast no-op on the matching hash.
        //
        // SOFT-PAUSE semantics: pass {@link StorageManipulationOpt#localCacheOnly()} so the
        // teardown unregisters every OAP-internal artefact (MeterSystem prototypes,
        // MetricsStreamProcessor entry / persistent workers, BatchQueue handlers, retired
        // RuleClassLoader) without firing the backend dropTable cascade. The measure / table
        // / index and any data already persisted under the pre-inactivate metric stay
        // intact — operators reactivate via {@code /addOrUpdate} and the existing data
        // remains queryable through the new bundle. {@code /delete} is the only path that
        // drops the backend schema.
        //
        // Teardown failure handling: surface as 500 teardown_deferred rather than 200
        // inactivated. The DB row IS INACTIVE (persist already succeeded above) so peers
        // converge from the DB — but on THIS node the OAP-internal teardown may not have
        // completed (MalFileApplier swallowed per-metric failures, MetricsStreamProcessor
        // worker drain threw, etc.). Returning 200 would tell the operator "done" while
        // dispatch is still live; 500 + "teardown_deferred" accurately signals retriable
        // state — the next dslManager tick re-runs the same localCacheOnly teardown.
        final RuntimeRuleManagementDAO.RuntimeRuleFile inactiveFile =
            new RuntimeRuleManagementDAO.RuntimeRuleFile(
                catalog, name, content,
                RuntimeRule.STATUS_INACTIVE, rule.getUpdateTime());
        try {
            dslManager.applyNowForRuleFile(inactiveFile, false,
                StorageManipulationOpt.localCacheOnly());
        } catch (final Throwable t) {
            log.warn("runtime-rule inactivate: local teardown deferred to tick for {}/{}",
                catalog, name, t);
            return serverError("teardown_deferred", catalog, name,
                "DB row flipped to INACTIVE, but local teardown threw — OAP-internal "
                    + "register cleanup on this node may not have completed. Tick will "
                    + "retry. Cause: " + t.getMessage());
        }
        return ok(HttpStatus.OK, staticTombstone ? "static_tombstoned" : "inactivated",
            catalog, name,
            staticTombstone
                ? "static rule tombstoned with INACTIVE row; local handlers unregistered; "
                    + "peers converge on next tick"
                : "status set to INACTIVE; local handlers unregistered; peers converge on next tick");
    }

    private HttpResponse doDelete(final String catalog, final String name, final DeleteMode mode) {
        return doDelete(catalog, name, mode, false);
    }

    private HttpResponse doDelete(final String catalog, final String name,
                                   final DeleteMode mode,
                                   final boolean forwarded) {
        final HttpResponse validationError = validate(catalog, name);
        if (validationError != null) {
            return validationError;
        }
        // Forward to main with the mode's wire value preserved as request body bytes — the
        // receiver unpacks it via executeDelete(..., new String(body, UTF_8)) and re-parses.
        // Empty body for DEFAULT.
        final byte[] modeBody = mode == DeleteMode.DEFAULT
            ? new byte[0]
            : mode.getWireValue().getBytes(StandardCharsets.UTF_8);
        final HttpResponse routed = routeOrNull(catalog, name, "delete",
            modeBody, false, false, forwarded);
        if (routed != null) {
            return routed;
        }
        final RuntimeRuleManagementDAO dao = resolveDao();
        if (dao == null) {
            return serverError("dao_unavailable", catalog, name,
                "RuntimeRuleManagementDAO not resolvable — storage module may not be active");
        }
        // Same locking contract as /inactivate.
        final ReentrantLock perFile = AppliedRuleScript.lockFor(dslManager.getRules(), catalog, name);
        if (!dslManager.getLockMetrics().acquireForRest(perFile, REST_LOCK_TIMEOUT_MS, catalog, name)) {
            return HttpResponse.of(HttpStatus.CONFLICT, MediaType.JSON_UTF_8,
                jsonBody("update_in_progress", catalog, name,
                    "another update for this rule file is in progress on this OAP; retry"));
        }
        try (HistogramMetrics.Timer ignored =
                 dslManager.getLockMetrics().startRestHoldTimer()) {
            return doDeleteLocked(catalog, name, mode, dao);
        } finally {
            perFile.unlock();
        }
    }

    private HttpResponse doDeleteLocked(final String catalog, final String name,
                                         final DeleteMode mode,
                                         final RuntimeRuleManagementDAO dao) {
        // /delete is the one destructive endpoint. /inactivate is a soft-pause that runs the
        // OAP-internal teardown under localCacheOnly, deliberately preserving the BanyanDB
        // measure + its data so a re-activation via /addOrUpdate is cheap and lossless.
        // /delete drops the backend measure first, then removes the tombstone row.
        //
        // The two-step workflow (/inactivate → /delete) is enforced by the INACTIVE-status
        // check below: an ACTIVE rule cannot be deleted in one shot. This separation makes
        // the destructive moment explicit and lets operators reverse the soft-pause for a
        // bounded window before committing to data loss.
        final RuntimeRuleManagementDAO.RuntimeRuleFile prior;
        try {
            prior = findRule(dao, catalog, name);
        } catch (final IOException ioe) {
            log.error("runtime-rule delete: prior-row lookup failed for {}/{}", catalog, name, ioe);
            return serverError("dao_unavailable", catalog, name,
                "prior-row lookup failed: " + ioe.getMessage());
        }
        if (prior == null) {
            // Idempotent: the desired end state (no row) is already achieved. Return 200 with
            // an explicit applyStatus so operators can distinguish a no-op from a real delete.
            return ok(HttpStatus.OK, "not_found", catalog, name,
                "no row present for this rule; nothing to delete");
        }
        if (!RuntimeRule.STATUS_INACTIVE.equals(prior.getStatus())) {
            return HttpResponse.of(HttpStatus.CONFLICT, MediaType.JSON_UTF_8,
                jsonBody("requires_inactivate_first", catalog, name,
                    "rule is ACTIVE; POST /runtime/rule/inactivate first, then /runtime/rule/delete. "
                        + "Inactivate runs the soft-pause (handlers stop dispatching; backend "
                        + "measure preserved); delete drops the backend measure and removes the row."));
        }

        final boolean bundledTwinExists =
            StaticRuleRegistry.active().find(catalog, name).isPresent();
        if (mode == DeleteMode.REVERT_TO_BUNDLED && !bundledTwinExists) {
            // Operator scripted the revert mode for a rule that has no bundled twin —
            // /delete cannot revert to anything. Surface a 400 with a clear error so the
            // script knows to either drop the mode flag or the operator's assumption was
            // wrong about which rules exist on disk.
            return badRequest("no_bundled_twin", catalog, name,
                "mode=revertToBundled requires a bundled YAML on disk for this "
                    + "(catalog, name); none was found");
        }

        // Backend drop. /inactivate preserved the BanyanDB measure under localCacheOnly;
        // discharge that debt now via the dslManager before the row goes away. The
        // orchestrator skips the destructive cascade when a bundled twin exists (bundled
        // will reuse the backend resource on the synchronous reload below). LAL has no
        // backend schema so the call is a no-op for the lal catalog. A throw here aborts
        // the row deletion — we do NOT proceed with dao.delete on backend-drop failure:
        // that would orphan the measure with no way to find it again.
        try {
            dslManager.getDslRuntimeDelete().dropBackendForDelete(catalog, name, prior.getContent());
        } catch (final IllegalStateException refused) {
            // Cross-file ownership conflict /addOrUpdate's guard didn't catch. Surface as
            // 409 so the operator sees a clear "fix and retry" signal rather than 500.
            log.warn("runtime-rule /delete refused for {}/{}: {}", catalog, name, refused.getMessage());
            return HttpResponse.of(HttpStatus.CONFLICT, MediaType.JSON_UTF_8,
                jsonBody("delete_refused", catalog, name, refused.getMessage()));
        } catch (final Throwable t) {
            log.error("runtime-rule /delete: backend drop threw for {}/{}", catalog, name, t);
            return serverError("delete_backend_drop_failed", catalog, name, t.getMessage());
        }
        try {
            dao.delete(catalog, name);
        } catch (final IOException e) {
            log.error("failed to delete runtime rule {}/{}", catalog, name, e);
            return serverError("delete_failed", catalog, name, e.getMessage());
        }

        // Synchronously reload the bundled rule (if any) so the operator's response
        // reflects the post-delete reality — bundled is already serving via a static:
        // loader on this node. Peer nodes converge via the gone-keys reconcile path on
        // their next tick. A reload failure is logged and surfaced as a partial-success
        // response (200 with applyStatus=reverted_to_bundled_partial) — the row is gone,
        // the operator's intent landed, but bundled didn't compile cleanly on this node.
        if (bundledTwinExists) {
            final boolean reloaded = dslManager.getDslRuntimeDelete()
                .reloadBundledIfPresent(catalog, name);
            return ok(HttpStatus.OK,
                reloaded ? "reverted_to_bundled" : "reverted_to_bundled_partial",
                catalog, name,
                reloaded
                    ? "runtime row removed; bundled rule reinstalled into a static: loader "
                        + "on this node; peers converge on next tick"
                    : "runtime row removed; bundled reload deferred (compile failed or "
                        + "engine unavailable); peers will retry via the gone-keys "
                        + "reconcile on their next tick");
        }
        return ok(HttpStatus.OK, "deleted", catalog, name,
            "backend measure dropped, runtime row removed from storage; rule is fully gone");
    }

    /**
     * Look up the current rule file for {@code (catalog, name)} via the DAO. Returns
     * {@code null} when no such rule exists; propagates {@link IOException} so callers that
     * need a definitive answer (notably {@link #doDeleteLocked}) can fail loud instead of
     * treating a DAO blip as "rule is absent".
     */
    private RuntimeRuleManagementDAO.RuntimeRuleFile findRule(final RuntimeRuleManagementDAO dao,
                                                              final String catalog,
                                                              final String name) throws IOException {
        for (final RuntimeRuleManagementDAO.RuntimeRuleFile r : dao.getAll()) {
            if (catalog.equals(r.getCatalog()) && name.equals(r.getName())) {
                return r;
            }
        }
        return null;
    }

    private HttpResponse validate(final String catalog, final String name) {
        if (catalog == null || catalog.isEmpty()) {
            return badRequest("missing_catalog", catalog, name, "catalog query parameter is required");
        }
        if (!isValidCatalog(catalog)) {
            return badRequest("invalid_catalog", catalog, name,
                "catalog must be one of " + validCatalogs());
        }
        if (name == null || name.isEmpty()) {
            return badRequest("missing_name", catalog, name, "name query parameter is required");
        }
        if (name.startsWith("/") || name.contains("..") || name.contains("\\")
            || name.contains("\u0000") || !VALID_NAME.matcher(name).matches()) {
            return badRequest("invalid_name", catalog, name,
                "name must match segments [A-Za-z0-9._-]+ joined by '/' with no leading slash, "
                    + "no '..', no empty segments, no backslash");
        }
        return null;
    }

    private RuntimeRuleManagementDAO resolveDao() {
        try {
            return moduleManager.find(StorageModule.NAME).provider()
                                .getService(RuntimeRuleManagementDAO.class);
        } catch (final Throwable t) {
            log.error("RuntimeRuleManagementDAO lookup failed", t);
            return null;
        }
    }

    private RuntimeRuleManagementDAO.RuntimeRuleFile findRuleFile(
            final RuntimeRuleManagementDAO dao, final String catalog, final String name) throws IOException {
        for (final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile : dao.getAll()) {
            if (catalog.equals(ruleFile.getCatalog()) && name.equals(ruleFile.getName())) {
                return ruleFile;
            }
        }
        return null;
    }

    // ----- Response helpers --------------------------------------------------------------------

    private static HttpResponse ok(final HttpStatus status, final String applyStatus,
                                   final String catalog, final String name, final String message) {
        return HttpResponse.of(status, MediaType.JSON_UTF_8, jsonBody(applyStatus, catalog, name, message));
    }

    private static HttpResponse badRequest(final String applyStatus, final String catalog,
                                           final String name, final String message) {
        return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.JSON_UTF_8,
            jsonBody(applyStatus, catalog, name, message));
    }

    private static HttpResponse serverError(final String applyStatus, final String catalog,
                                            final String name, final String message) {
        return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.JSON_UTF_8,
            jsonBody(applyStatus, catalog, name, message));
    }

    private static HttpResponse notImplemented(final String op, final String catalog, final String name) {
        final JsonObject body = new JsonObject();
        body.addProperty("applyStatus", "not_implemented");
        body.addProperty("op", op);
        body.addProperty("catalog", catalog == null ? "" : catalog);
        body.addProperty("name", name == null ? "" : name);
        return HttpResponse.of(HttpStatus.NOT_IMPLEMENTED, MediaType.JSON_UTF_8, GSON.toJson(body));
    }

    private static String jsonBody(final String applyStatus, final String catalog,
                                   final String name, final String message) {
        final JsonObject body = new JsonObject();
        body.addProperty("applyStatus", applyStatus);
        body.addProperty("catalog", catalog == null ? "" : catalog);
        body.addProperty("name", name == null ? "" : name);
        body.addProperty("message", message == null ? "" : message);
        return GSON.toJson(body);
    }

    /** {@link #jsonBody} variant that also carries the resolved cluster-main address — used
     *  by the routing-failure responses ({@code cluster_view_split}, {@code forward_failed})
     *  so the operator sees which peer was attempted. */
    private static String routingErrorBody(final String applyStatus, final String catalog,
                                           final String name, final String mainNode,
                                           final String message) {
        final JsonObject body = new JsonObject();
        body.addProperty("applyStatus", applyStatus);
        body.addProperty("catalog", catalog == null ? "" : catalog);
        body.addProperty("name", name == null ? "" : name);
        body.addProperty("mainNode", mainNode == null ? "" : mainNode);
        body.addProperty("message", message == null ? "" : message);
        return GSON.toJson(body);
    }

    /**
     * RFC 8259 §7 JSON-string escape. Handles {@code "}, {@code \}, control chars
     * (newlines, tabs, etc. — the case that the original two-char replace did NOT cover),
     * and other characters below {@code U+0020}. Non-ASCII printable characters pass through
     * unchanged — the response is {@code application/json; charset=utf-8} so multi-byte
     * UTF-8 (Chinese comments, emoji in tags) is carried as-is.
     */
    private static String escape(final String s) {
        if (s == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Render one /list line for a rule that exists in storage. {@code local} is the dslManager's
     * DSLRuntimeState (may be null if the dslManager hasn't observed this row yet — first-tick
     * window). The merged line carries both the persisted and transient per-node pieces so an
     * operator sees everything needed to diagnose convergence gaps.
     */
    private static JsonObject renderListEntry(final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile,
                                              final DSLRuntimeState local) {
        final JsonObject row = new JsonObject();
        row.addProperty("catalog", ruleFile.getCatalog());
        row.addProperty("name", ruleFile.getName());
        row.addProperty("status", ruleFile.getStatus());
        row.addProperty("localState", local == null
            ? DSLRuntimeState.LocalState.NOT_LOADED.name()
            : local.getLocalState().name());
        row.addProperty("suspendOrigin", local == null
            ? DSLRuntimeState.SuspendOrigin.NONE.name()
            : local.getSuspendOrigin().name());
        row.addProperty("loaderGc", local == null
            ? DSLRuntimeState.LoaderGc.LIVE.name()
            : local.getLoaderGc().name());
        addLoaderFields(row, ruleFile.getCatalog(), ruleFile.getName());
        row.addProperty("contentHash", ContentHash.sha256Hex(ruleFile.getContent()));
        addBundledFields(row, ruleFile.getCatalog(), ruleFile.getName());
        row.addProperty("updateTime", ruleFile.getUpdateTime());
        row.addProperty("lastApplyError",
            local == null || local.getLastApplyError() == null ? "" : local.getLastApplyError());
        return row;
    }

    /** Look up the per-rule loader the manager has installed for {@code (catalog, name)} and
     *  add {@code loaderKind} / {@code loaderName} to {@code row}. {@code loaderKind=NONE}
     *  with empty {@code loaderName} when no per-file loader exists for this key (typical
     *  for bundled-only rules served from the shared default loader). */
    private static void addLoaderFields(final JsonObject row, final String catalog,
                                        final String name) {
        final Catalog c;
        try {
            c = Catalog.of(catalog);
        } catch (final IllegalArgumentException unknown) {
            row.addProperty("loaderKind", "NONE");
            row.addProperty("loaderName", "");
            return;
        }
        final Optional<RuleClassLoader> loader =
            DSLClassLoaderManager.INSTANCE.active(c, name);
        row.addProperty("loaderKind",
            loader.map(l -> l.getKind().name()).orElse("NONE"));
        row.addProperty("loaderName", loader.map(RuleClassLoader::getName).orElse(""));
    }

    /** Add {@code bundled} (boolean) and {@code bundledContentHash} (string, omitted when
     *  no bundled twin exists) so the UI can render an "Override" / "Modified from bundled"
     *  badge without a second roundtrip to {@code /bundled}. */
    private static void addBundledFields(final JsonObject row, final String catalog,
                                         final String name) {
        final Optional<String> bundled = StaticRuleRegistry.active().find(catalog, name);
        row.addProperty("bundled", bundled.isPresent());
        bundled.ifPresent(content ->
            row.addProperty("bundledContentHash", ContentHash.sha256Hex(content)));
    }

    /**
     * Assemble a tar.gz of rule rows. Nested directory layout mirrors the on-disk static
     * catalog tree so the archive is re-POSTable through {@code addOrUpdate} for DR restore.
     * ACTIVE rows go under {@code <catalog>/<name>.yaml}; INACTIVE rows go under
     * {@code inactive/<catalog>/<name>.yaml} so restore can reproduce tombstones explicitly.
     * A top-level {@code manifest.yaml} records per-row metadata for audit / integrity.
     */
    private HttpResponse doDump(final String catalogFilter) {
        final RuntimeRuleManagementDAO dao = resolveDao();
        if (dao == null) {
            return serverError("dao_unavailable", catalogFilter, null,
                "RuntimeRuleManagementDAO not resolvable — storage module may not be active");
        }
        final List<RuntimeRuleManagementDAO.RuntimeRuleFile> ruleFiles;
        try {
            ruleFiles = dao.getAll();
        } catch (final IOException e) {
            return serverError("dump_failed", catalogFilter, null, e.getMessage());
        }

        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final DateTimeFormatter iso = DateTimeFormatter.ISO_INSTANT;
        final String dumpedAt = iso.format(Instant.now());
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            final StringBuilder manifest = new StringBuilder();
            manifest.append("dumpedAt: \"").append(dumpedAt).append("\"\n");
            manifest.append("catalogFilter: \"")
                   .append(catalogFilter == null ? "" : escape(catalogFilter))
                   .append("\"\n");
            manifest.append("entries:\n");

            int emitted = 0;
            for (final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile : ruleFiles) {
                if (catalogFilter != null && !catalogFilter.equals(ruleFile.getCatalog())) {
                    continue;
                }
                emitted++;
                final boolean inactive = RuntimeRule.STATUS_INACTIVE.equals(ruleFile.getStatus());
                final String prefix = inactive ? "runtime-rule-dump/inactive/" : "runtime-rule-dump/";
                final String entryPath = prefix + ruleFile.getCatalog() + "/" + ruleFile.getName() + ".yaml";
                writeTarEntry(tar, entryPath, ruleFile.getContent());

                final String sha = ContentHash.sha256Hex(ruleFile.getContent());
                manifest.append("  - catalog: ").append(ruleFile.getCatalog()).append("\n");
                manifest.append("    name: \"").append(escape(ruleFile.getName())).append("\"\n");
                manifest.append("    status: ").append(ruleFile.getStatus()).append("\n");
                manifest.append("    updateTime: ").append(ruleFile.getUpdateTime()).append("\n");
                manifest.append("    sha256: \"").append(sha).append("\"\n");
            }
            writeTarEntry(tar, "runtime-rule-dump/manifest.yaml", manifest.toString());
            log.info("runtime-rule dump assembled: {} row(s) {}",
                emitted, catalogFilter == null ? "(all catalogs)" : "(catalog=" + catalogFilter + ")");
        } catch (final IOException e) {
            return serverError("dump_failed", catalogFilter, null, e.getMessage());
        }

        final String filename = "runtime-rule-dump-" + dumpedAt.replace(":", "-") + ".tar.gz";
        return HttpResponse.of(
            ResponseHeaders.builder(HttpStatus.OK)
                .contentType(MediaType.OCTET_STREAM)
                .add(HttpHeaderNames.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"")
                .build(),
            HttpData.copyOf(buffer.toByteArray()));
    }

    private static void writeTarEntry(
            final TarArchiveOutputStream tar,
            final String path, final String body) throws IOException {
        final byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        final TarArchiveEntry entry = new TarArchiveEntry(path);
        entry.setSize(bytes.length);
        tar.putArchiveEntry(entry);
        tar.write(bytes);
        tar.closeArchiveEntry();
    }

    /**
     * Render a /list row for an in-memory bundle whose runtime row has been deleted but the
     * dslManager hasn't yet swept — transient, typically gone within one tick. Surfacing it
     * helps operators watching a delete propagate.
     */
    private static JsonObject renderOrphanEntry(final DSLRuntimeState local) {
        final JsonObject row = new JsonObject();
        row.addProperty("catalog", local.getCatalog());
        row.addProperty("name", local.getName());
        row.addProperty("status", "n/a");
        row.addProperty("localState", local.getLocalState().name());
        row.addProperty("loaderGc", local.getLoaderGc().name());
        addLoaderFields(row, local.getCatalog(), local.getName());
        row.addProperty("contentHash", local.getContentHash());
        addBundledFields(row, local.getCatalog(), local.getName());
        row.addProperty("pendingUnregister", true);
        return row;
    }

    /**
     * Render a /list row for a bundled-only rule — shipped on disk, no runtime override.
     * Status is reported as {@code BUNDLED} so operators can distinguish it from an
     * {@code ACTIVE} runtime override and from the transient orphan state.
     */
    private static JsonObject renderBundledEntry(final DSLRuntimeState local) {
        final JsonObject row = new JsonObject();
        row.addProperty("catalog", local.getCatalog());
        row.addProperty("name", local.getName());
        row.addProperty("status", "BUNDLED");
        row.addProperty("localState", local.getLocalState().name());
        row.addProperty("loaderGc", local.getLoaderGc().name());
        addLoaderFields(row, local.getCatalog(), local.getName());
        row.addProperty("contentHash", local.getContentHash());
        addBundledFields(row, local.getCatalog(), local.getName());
        row.addProperty("pendingUnregister", false);
        return row;
    }
}
