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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.AliasRegistrar;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.KnownServices;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.SpanPairing;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TraceAnalyzerModuleConfig;

/**
 * The runtime services a compiled rule calls into, shared by every span the rule sees. One instance per rule;
 * all per-span state lives on the {@link ExecutionContext} passed to each method.
 */
@Slf4j
public class FilterSpec {
    @Getter
    private final ModuleManager moduleManager;
    @Getter
    private final TraceAnalyzerModuleConfig config;
    @Getter
    private final MeterSystem meterSystem;
    @Getter
    private final NamingControl namingControl;
    @Getter
    private final SourceReceiver sourceReceiver;
    /**
     * The service names seen on spans; the module's shared registry in production, a fresh one for a caller
     * that builds the spec from the services it holds.
     */
    @Getter
    private final KnownServices knownServices;
    /**
     * The call pairs waiting for their other half; the module's shared buffers in production, fresh ones for a
     * caller that builds the spec from the services it holds.
     */
    @Getter
    private final SpanPairing spanPairing;
    /**
     * Where the rule's {@code alias} statement and a learned pair both write their rows.
     */
    @Getter
    private final AliasRegistrar aliasRegistrar;

    /**
     * The rate-limit counters by id; the module's shared set in production, so two rules naming one id share
     * one budget, and a fresh set for a caller that builds the spec from the services it holds.
     */
    private final RateLimitingSamplers samplers;

    public FilterSpec(final ModuleManager moduleManager, final TraceAnalyzerModuleConfig config) {
        this(moduleManager, config,
            moduleManager.find(CoreModule.NAME).provider().getService(MeterSystem.class),
            moduleManager.find(CoreModule.NAME).provider().getService(NamingControl.class),
            moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class),
            moduleManager.find(TraceAnalyzerModule.NAME).provider().getService(KnownServices.class),
            moduleManager.find(TraceAnalyzerModule.NAME).provider().getService(SpanPairing.class),
            moduleManager.find(TraceAnalyzerModule.NAME).provider().getService(RateLimitingSamplers.class));
    }

    /**
     * For a caller that already holds the core services, which is what makes the runtime exercisable without a
     * booted module system.
     *
     * @param moduleManager  the manager, for what a rule resolves lazily
     * @param config         the module's configuration
     * @param meterSystem    where metric values go
     * @param namingControl  the naming rules every entity name passes through
     * @param sourceReceiver where output records go
     */
    public FilterSpec(final ModuleManager moduleManager,
                      final TraceAnalyzerModuleConfig config,
                      final MeterSystem meterSystem,
                      final NamingControl namingControl,
                      final SourceReceiver sourceReceiver) {
        this(moduleManager, config, meterSystem, namingControl, sourceReceiver, new KnownServices(), new SpanPairing(),
            new RateLimitingSamplers());
    }

    private FilterSpec(final ModuleManager moduleManager,
                       final TraceAnalyzerModuleConfig config,
                       final MeterSystem meterSystem,
                       final NamingControl namingControl,
                       final SourceReceiver sourceReceiver,
                       final KnownServices knownServices,
                       final SpanPairing spanPairing,
                       final RateLimitingSamplers samplers) {
        this.moduleManager = moduleManager;
        this.config = config;
        this.meterSystem = meterSystem;
        this.namingControl = namingControl;
        this.sourceReceiver = sourceReceiver;
        this.knownServices = knownServices;
        this.spanPairing = spanPairing;
        this.samplers = samplers;
        // The module's registrar carries the guards that need a booted OAP; a spec built without one makes its own.
        if (spanPairing.getAliasRegistrar() == null) {
            spanPairing.setAliasRegistrar(new AliasRegistrar(sourceReceiver, namingControl, knownServices));
        }
        this.aliasRegistrar = spanPairing.getAliasRegistrar();
    }

    /**
     * {@code dropper {}} — vote to drop.
     *
     * @param ctx      this span's state
     * @param inPersist whether the statement sits in a {@code persist} block, so the span is what it drops
     */
    public void dropper(final ExecutionContext ctx, final boolean inPersist) {
        if (inPersist) {
            ctx.votePersist(false);
        } else {
            ctx.dropRecord();
        }
    }

    /**
     * {@code enforcer {}} — keep regardless of this rule's samplers, before or after it, and in a {@code persist}
     * block ask the forward to skip the receiver's trace-id head sampling for this span.
     *
     * @param ctx       this span's state
     * @param inPersist whether the statement sits in a {@code persist} block
     */
    public void enforcer(final ExecutionContext ctx, final boolean inPersist) {
        if (inPersist) {
            ctx.enforce();
        } else {
            ctx.enforceRecord();
        }
    }

    /**
     * {@code rateLimit(id) { rpm n }} — one counter per id, reset every minute, shared by every rule and span
     * that names the same id. It counts spans, not traces, so two spans of one trace can fall on different
     * sides of the budget. A span or record an {@code enforcer {}} already kept neither votes nor spends budget.
     *
     * @param ctx       this span's state
     * @param id        the counter's id, already interpolated
     * @param rpm       the budget per minute
     * @param inPersist whether the sampler sits in a {@code persist} block
     */
    public void rateLimit(final ExecutionContext ctx, final String id, final int rpm, final boolean inPersist) {
        if (inPersist ? ctx.isEnforced() : ctx.isRecordEnforced()) {
            return;
        }
        if (samplers.of(id, rpm).sample()) {
            if (inPersist) {
                ctx.votePersist(true);
            }
        } else if (inPersist) {
            ctx.sampleOutSpan();
        } else {
            ctx.sampleOutRecord();
        }
    }
}
