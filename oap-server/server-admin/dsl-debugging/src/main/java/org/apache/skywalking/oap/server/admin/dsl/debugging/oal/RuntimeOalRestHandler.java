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

package org.apache.skywalking.oap.server.admin.dsl.debugging.oal;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.Blocking;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.DispatcherManager;
import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.dsldebug.DebugHolderProvider;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.core.source.ISource;

/**
 * Read-only OAL listing endpoints under {@code /runtime/oal/*}. The DSL debug
 * UI / CLI uses these to populate its rule picker without having to know which
 * OAL bundles a particular OAP build ships. They never mutate state — neither
 * static files on disk nor the activated {@link OALDefine} set in the engine
 * loader — and they are always safe to expose on the admin port (subject to
 * the admin port's overall gateway protection).
 *
 * <p>{@code /files} + {@code /files/{name}} feed off the loaded
 * {@link OALDefine} set; {@code /rules} + {@code /rules/{ruleName}} walk the
 * dispatcher set via {@link DispatcherManager#snapshotDispatchers()} and ask
 * each {@link DebugHolderProvider} for its rule names — same source of truth
 * the session install path uses, so a rule listed here is guaranteed to
 * resolve a holder when a session targets it.
 */
@Blocking
@Slf4j
public class RuntimeOalRestHandler {

    private static final Gson GSON = new Gson();

    private final OALEngineLoaderService oalEngineLoaderService;
    private final DispatcherManager dispatcherManager;

    public RuntimeOalRestHandler(final OALEngineLoaderService oalEngineLoaderService,
                                 final DispatcherManager dispatcherManager) {
        this.oalEngineLoaderService = oalEngineLoaderService;
        this.dispatcherManager = dispatcherManager;
    }

    @Get("/runtime/oal/files")
    public HttpResponse listFiles() {
        final Set<OALDefine> defines = oalEngineLoaderService.getLoadedDefines();
        final Set<String> names = new TreeSet<>();
        for (final OALDefine define : defines) {
            names.add(define.getConfigFile());
        }
        final JsonObject root = new JsonObject();
        final JsonArray arr = new JsonArray();
        names.forEach(arr::add);
        root.add("files", arr);
        root.addProperty("count", names.size());
        return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, GSON.toJson(root));
    }

    @Get("/runtime/oal/files/{name}")
    public HttpResponse getFile(@Param("name") final String name) {
        if (!isKnownConfigFile(name)) {
            return HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.PLAIN_TEXT_UTF_8,
                                   "OAL file not loaded: " + name);
        }
        final byte[] content = readClasspathBytes(name);
        if (content == null) {
            return HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.PLAIN_TEXT_UTF_8,
                                   "OAL file content not found on classpath: " + name);
        }
        return HttpResponse.of(HttpStatus.OK,
                               MediaType.PLAIN_TEXT_UTF_8,
                               HttpData.wrap(content));
    }

    /**
     * One row per dispatcher (per OAL source). Each row carries the source's
     * full metric set so the UI can render "source X routes A, B, C" alongside
     * the picker. A debug session for OAL targets a source — every metric
     * routed off that source captures together.
     */
    @Get("/runtime/oal/rules")
    public HttpResponse listRules() {
        final JsonArray sources = new JsonArray();
        if (dispatcherManager != null) {
            for (final SourceDispatcher<ISource> dispatcher : dispatcherManager.snapshotDispatchers()) {
                if (!(dispatcher instanceof DebugHolderProvider)) {
                    continue;
                }
                final DebugHolderProvider provider = (DebugHolderProvider) dispatcher;
                final JsonObject entry = new JsonObject();
                entry.addProperty("source", sourceNameOf(dispatcher));
                entry.addProperty("dispatcher", dispatcher.getClass().getName());
                final JsonArray metrics = new JsonArray();
                for (final String metric : provider.debugRuleNames()) {
                    metrics.add(metric);
                }
                entry.add("metrics", metrics);
                sources.add(entry);
            }
        }
        final JsonObject body = new JsonObject();
        body.add("sources", sources);
        body.addProperty("count", sources.size());
        return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, GSON.toJson(body));
    }

    /**
     * Single-source detail. {@code source} is the OAL source class name
     * (e.g. {@code Endpoint}) — same value the session install path takes
     * as {@code RuleKey.name}.
     */
    @Get("/runtime/oal/rules/{source}")
    public HttpResponse getRule(@Param("source") final String source) {
        if (source == null || source.isEmpty()) {
            return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.JSON_UTF_8,
                                   GSON.toJson(errorBody("missing_source",
                                                         "source path param is required")));
        }
        if (dispatcherManager == null) {
            return HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.JSON_UTF_8,
                                   GSON.toJson(errorBody("source_not_found",
                                                         "OAL dispatcher manager unavailable")));
        }
        for (final SourceDispatcher<ISource> dispatcher : dispatcherManager.snapshotDispatchers()) {
            if (!(dispatcher instanceof DebugHolderProvider)) {
                continue;
            }
            if (!source.equals(sourceNameOf(dispatcher))) {
                continue;
            }
            final DebugHolderProvider provider = (DebugHolderProvider) dispatcher;
            final JsonObject entry = new JsonObject();
            entry.addProperty("source", source);
            entry.addProperty("dispatcher", dispatcher.getClass().getName());
            // Per-metric gate: list each metric with its holder status. A "live"
            // status means the metric's GateHolder exists and is wired; "no_holder"
            // marks a metric that the codegen knew about but didn't generate a holder
            // for (test mocks, dispatchers compiled before SWIP-13).
            final JsonArray metrics = new JsonArray();
            for (final String metric : provider.debugRuleNames()) {
                final JsonObject metricEntry = new JsonObject();
                metricEntry.addProperty("name", metric);
                metricEntry.addProperty("status",
                    provider.debugHolder(metric) == null ? "no_holder" : "live");
                metrics.add(metricEntry);
            }
            entry.add("metrics", metrics);
            return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, GSON.toJson(entry));
        }
        return HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.JSON_UTF_8,
                               GSON.toJson(errorBody("source_not_found",
                                                     "No OAL dispatcher owns source " + source)));
    }

    /**
     * Strip the {@code Dispatcher} suffix off the dispatcher class's simple
     * name to get the OAL source name — same convention {@code DispatcherManager}
     * uses for its source-name → dispatcher matching.
     */
    private static String sourceNameOf(final SourceDispatcher<ISource> dispatcher) {
        final String simple = dispatcher.getClass().getSimpleName();
        final int suffix = simple.indexOf("Dispatcher");
        return suffix > 0 ? simple.substring(0, suffix) : simple;
    }

    private static JsonObject errorBody(final String code, final String message) {
        final JsonObject body = new JsonObject();
        body.addProperty("status", "error");
        body.addProperty("code", code);
        body.addProperty("message", message);
        return body;
    }

    private boolean isKnownConfigFile(final String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (final OALDefine define : oalEngineLoaderService.getLoadedDefines()) {
            if (name.equals(define.getConfigFile())) {
                return true;
            }
        }
        return false;
    }

    private byte[] readClasspathBytes(final String resource) {
        // OALDefine#configFile is a classpath-relative resource (e.g. "core.oal").
        // We never resolve a filesystem path here — the input set is bounded by the
        // loaded define list and the loader itself reads from classpath.
        try (InputStream in = OALEngineLoaderService.class.getClassLoader()
                                                          .getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (final IOException e) {
            log.warn("DSL debug: failed to read OAL classpath resource '{}'", resource, e);
            return null;
        }
    }

}
