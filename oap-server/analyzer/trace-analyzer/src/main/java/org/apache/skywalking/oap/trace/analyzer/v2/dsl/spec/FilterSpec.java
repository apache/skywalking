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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.AliasRegistrar;
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
     * The call pairs waiting for their other half, the module's shared buffers.
     */
    @Getter
    private final SpanPairing spanPairing;
    /**
     * Where a built pair writes its row, and which addresses are uninstrumented gateways.
     */
    @Getter
    private final AliasRegistrar aliasRegistrar;

    /**
     * The rate-limit counters by id, the module's shared set, so two rules naming one id share one budget.
     */
    private final RateLimitingSamplers samplers;

    /**
     * The services the input's functions read, by type, with the module the first lookup of the type named, so the
     * usual lookup is one probe; {@link #servicesByModule} answers a type another module also serves.
     */
    private final Map<Class<?>, ModuleService> services = new ConcurrentHashMap<>();

    /**
     * Every service looked up, by module, then by type.
     */
    private final Map<String, Map<Class<?>, Object>> servicesByModule = new ConcurrentHashMap<>();

    public FilterSpec(final ModuleManager moduleManager, final TraceAnalyzerModuleConfig config) {
        this.moduleManager = moduleManager;
        this.config = config;
        final ModuleServiceHolder core = moduleManager.find(CoreModule.NAME).provider();
        this.meterSystem = core.getService(MeterSystem.class);
        this.namingControl = core.getService(NamingControl.class);
        this.sourceReceiver = core.getService(SourceReceiver.class);
        final ModuleServiceHolder analyzer = moduleManager.find(TraceAnalyzerModule.NAME).provider();
        this.spanPairing = analyzer.getService(SpanPairing.class);
        this.samplers = analyzer.getService(RateLimitingSamplers.class);
        // Set by the module's start() before the first rule compiles, see TraceAnalyzerModuleProvider.
        this.aliasRegistrar = spanPairing.getAliasRegistrar();
    }

    /**
     * {@code dropper {}} — vote to drop the span.
     *
     * @param ctx this span's state
     */
    public void dropper(final ExecutionContext ctx) {
        ctx.votePersist(false);
    }

    /**
     * {@code enforcer {}} — keep the span regardless of this rule's samplers, before or after it, and ask the forward
     * to skip the receiver's trace-id head sampling for it.
     *
     * @param ctx this span's state
     */
    public void enforcer(final ExecutionContext ctx) {
        ctx.enforce();
    }

    /**
     * {@code rateLimit(id) { rpm n }} — one counter per id, reset every minute, shared by every rule and span
     * that names the same id. It counts spans, not traces, so two spans of one trace can fall on different
     * sides of the budget. A span an {@code enforcer {}} already kept neither votes nor spends budget.
     *
     * @param ctx this span's state
     * @param id  the counter's id, already interpolated
     * @param rpm the budget per minute
     */
    public void rateLimit(final ExecutionContext ctx, final String id, final int rpm) {
        if (ctx.isEnforced()) {
            return;
        }
        if (samplers.of(id, rpm).sample()) {
            ctx.votePersist(true);
        } else {
            ctx.sampleOutSpan();
        }
    }

    /**
     * A module's service, looked up once per rule and kept, for the functions an input declares, which read their
     * services through the run.
     *
     * @param module the module serving it
     * @param type   the service
     * @param <T>    the service's type
     * @return the service, or null when the module does not serve it
     */
    public <T extends Service> T service(final String module, final Class<T> type) {
        final ModuleService cached = services.get(type);
        if (cached != null && cached.module.equals(module)) {
            return type.cast(cached.service);
        }
        final Object service = servicesByModule.computeIfAbsent(module, key -> new ConcurrentHashMap<>())
                                               .computeIfAbsent(type, key -> moduleManager.find(module).provider()
                                                                                          .getService(type));
        if (cached == null && service != null) {
            services.putIfAbsent(type, new ModuleService(module, service));
        }
        return type.cast(service);
    }

    @RequiredArgsConstructor
    private static final class ModuleService {
        private final String module;
        private final Object service;
    }
}
