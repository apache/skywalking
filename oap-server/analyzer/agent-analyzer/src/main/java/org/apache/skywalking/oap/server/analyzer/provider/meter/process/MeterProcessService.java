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

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.meter.analyzer.v2.MalConverterRegistry;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.v2.dsldebug.MalStaticBindingHook;
import org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule.Rule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Management all of the meter builders.
 *
 * <p>Doubles as the {@link MalConverterRegistry} for the {@code meter-analyzer-config} catalog,
 * so the runtime-rule plugin can hot-swap a single meter rule without rebuilding the whole list.
 * The registry backs BOTH native-meter ingest paths — the gRPC meter receiver and the Kafka meter
 * fetcher — because both resolve their converters through this one service via
 * {@link IMeterProcessService}.
 *
 * <p>Thread-safety mirrors the otel receiver's registry: a volatile map replaced wholesale under
 * {@link #convertersWriteLock}, so ingest threads iterating {@link #converts()} always observe a
 * complete pre- or post-swap snapshot, never a torn intermediate. {@link MeterProcessor} re-reads
 * {@link #converts()} on every batch, so a hot-added rule takes effect on the next batch with no
 * OAP restart.
 */
public class MeterProcessService implements IMeterProcessService, MalConverterRegistry {

    /** Catalog wire-name; also the key namespace shared with runtime-rule's converter pushes. */
    private static final String CATALOG = "meter-analyzer-config";

    private final ModuleManager manager;
    /**
     * Copy-on-write snapshot keyed by {@code "<catalog>:<ruleName>"}. {@link LinkedHashMap}
     * preserves rule order so dispatch order stays deterministic across restarts.
     */
    private volatile Map<String, MetricConvert> converters = Collections.emptyMap();
    private final Object convertersWriteLock = new Object();

    public MeterProcessService(ModuleManager manager) {
        this.manager = manager;
    }

    /**
     * Compile and install every boot-time meter rule. Uses the same install path as a runtime
     * hot-update ({@link #addOrReplaceConverter}) so there is exactly one installation route,
     * and publishes each rule's per-metric debug holders so a dsl-debugging session can bind to
     * a bundled meter rule the same way it binds to an otel one.
     */
    public void start(List<Rule> rules) {
        final MeterSystem meterSystem = manager.find(CoreModule.NAME).provider().getService(MeterSystem.class);
        for (final Rule rule : rules) {
            final MetricConvert convert = new MetricConvert(rule, meterSystem);
            addOrReplaceConverter(CATALOG + ":" + rule.getName(), convert);
            // No-op unless the dsl-debugging module installed a sink.
            MalStaticBindingHook.publish(CATALOG, rule.getName(), convert);
        }
    }

    /**
     * Generate a new processor when receive meter data.
     */
    @Override
    public MeterProcessor createProcessor() {
        return new MeterProcessor(this);
    }

    /**
     * Getting all converters. Never null; empty before {@link #start} runs.
     */
    public Collection<MetricConvert> converts() {
        return converters.values();
    }

    @Override
    public void addOrReplaceConverter(final String key, final MetricConvert convert) {
        synchronized (convertersWriteLock) {
            final Map<String, MetricConvert> copy = new LinkedHashMap<>(converters);
            copy.put(key, convert);
            converters = Collections.unmodifiableMap(copy);
        }
    }

    @Override
    public void removeConverter(final String key) {
        synchronized (convertersWriteLock) {
            if (!converters.containsKey(key)) {
                return;
            }
            final Map<String, MetricConvert> copy = new LinkedHashMap<>(converters);
            copy.remove(key);
            converters = Collections.unmodifiableMap(copy);
        }
    }

}
