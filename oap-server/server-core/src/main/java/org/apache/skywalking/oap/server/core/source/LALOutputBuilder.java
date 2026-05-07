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

package org.apache.skywalking.oap.server.core.source;

import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Interface for LAL output builders that produce {@link Source} objects from
 * LAL extractor output fields.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and
 * can be referenced in LAL rule YAML config by their short {@link #name()}
 * (e.g., {@code outputType: SlowSQL}) instead of the fully qualified class name.
 *
 * <p>The LAL compiler validates output field assignments against the builder's
 * setters at compile time. At runtime:
 * <ol>
 *   <li>{@link #init} is called to pre-populate standard fields from
 *       {@code LogData} (metadata carrier) and an optional extra log object
 *       whose type matches what {@code LALSourceTypeProvider#inputType()}
 *       declares for the layer</li>
 *   <li>Output field values are applied via reflection (setter invocation)</li>
 *   <li>{@link #complete} is called to validate, create final Source/Record,
 *       and dispatch via {@link SourceReceiver}</li>
 * </ol>
 */
public interface LALOutputBuilder {
    /**
     * Short name used in LAL rule YAML config {@code outputType} field.
     * Must be unique across all implementations.
     */
    String name();

    /**
     * Cache the typed input + populate standard metadata-derived fields
     * the LAL extractor hasn't already set (service, instance, endpoint,
     * layer, trace context, timestamp). Called by the generated LAL
     * {@code execute()} body immediately after {@code ctx.setOutput(...)},
     * so the builder is fully populated before the extractor runs and
     * before any debug probe fires.
     *
     * <p>Does NOT require {@link ModuleManager}: this is the lightweight
     * lifecycle step. The {@code moduleManager}-dependent setup
     * (e.g. {@code NamingControl} resolution) happens at sink time via
     * {@link #init} which composes {@code ensureInitialized + bindInput}.
     *
     * <p>Default — no-op (subclasses override to cache their typed
     * input). Idempotent: safe to call multiple times per log; the
     * second call from {@link #init} re-runs the same logic without
     * harm.
     *
     * @param metadata uniform metadata (service, layer, timestamp, trace context, ...)
     * @param input    source-specific input object ({@code LogData} for standard logs,
     *                 {@code HTTPAccessLogEntry} for envoy access logs, etc.)
     */
    default void bindInput(LogMetadata metadata, Object input) {
    }

    /**
     * Full lifecycle init — runs {@code ensureInitialized(moduleManager)}
     * for first-call static caching (e.g. {@code NamingControl}), then
     * {@link #bindInput}. Called by the sink-stage receiver
     * ({@code RecordSinkListener.parse}) after the LAL extractor finishes.
     *
     * @param metadata      uniform metadata
     * @param input         source-specific input object
     * @param moduleManager module manager for resolving services (e.g., NamingControl)
     */
    void init(LogMetadata metadata, Object input, ModuleManager moduleManager);

    /**
     * Validate the builder state and dispatch the final output source(s).
     * Called after all output fields have been set.
     */
    void complete(SourceReceiver sourceReceiver);

    /**
     * Render the output entity this builder constructed as a debug JSON
     * string. The implementer reads its own typed fields (the columns
     * it copied from input + the LAL extractor's output assignments)
     * and emits them directly — Gson {@code JsonObject} field-by-field,
     * no reflection.
     *
     * <p>Called from {@code ExecutionContext.outputPayloadJson} on every
     * non-input sample (block / statement / function / output) — must be
     * cheap; do not allocate the final entity (no {@code toLog()}). For
     * envoy ALS, {@code EnvoyAccessLogBuilder} caches the proto-as-JSON
     * on init so this method is a constant-time field read.
     *
     * <p>The framework renders the raw input directly off
     * {@code ctx.input()} on the first (input-type) sample using its
     * native dispatcher; the builder is not consulted there because
     * {@link #init} has not yet fired at that probe.
     */
    String outputToJson();
}
