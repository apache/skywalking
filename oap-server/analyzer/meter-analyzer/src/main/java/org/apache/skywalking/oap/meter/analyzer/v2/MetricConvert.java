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

package org.apache.skywalking.oap.meter.analyzer.v2;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Try;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.FilterExpression;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;

import static java.util.stream.Collectors.toList;

/**
 * MetricConvert converts {@link SampleFamily} collection to meter-system metrics, then store them to backend storage.
 *
 * <p>One MetricConvert instance is created per MAL config YAML file (e.g., {@code vm.yaml}).
 * It holds a list of {@link Analyzer}s, one per {@code metricsRules} entry in the YAML.
 *
 * <p>Construction (at startup):
 * <pre>
 *   YAML file (e.g., vm.yaml)
 *     metricPrefix: meter_vm
 *     expSuffix:    service(['host'], Layer.OS_LINUX)
 *     filter:       { tags -&gt; tags.job_name == 'vm-monitoring' }
 *     metricsRules:
 *       - name: cpu_total_percentage
 *         exp:  (node_cpu_seconds_total * 100).sum(['host']).rate('PT1M')
 *
 *   MetricConvert(rule, meterSystem)
 *     for each rule:
 *       metricName = metricPrefix + "_" + name    → "meter_vm_cpu_total_percentage"
 *       finalExp   = (exp).expSuffix              → "(...).service(['host'], Layer.OS_LINUX)"
 *       → Analyzer.build(metricName, filter, finalExp, meterSystem)
 * </pre>
 *
 * <p>Runtime ({@link #toMeter}): receives the full {@code sampleFamilies} map (all metrics
 * from one scrape) and broadcasts it to every Analyzer. Each Analyzer self-filters to only
 * the input metrics it needs (via {@code this.samples} from compile-time metadata).
 */
@Slf4j
public class MetricConvert {

    public static <T> Stream<T> log(Try<T> t, String debugMessage) {
        return t
            .onSuccess(i -> log.debug(debugMessage + " :{}", i))
            .onFailure(e -> log.debug(debugMessage + " failed", e))
            .toJavaStream();
    }

    private final List<Analyzer> analyzers;

    public MetricConvert(MetricRuleConfig rule, MeterSystem service) {
        // Static boot default: create-if-absent semantics. Runtime-rule on-demand callers use
        // the explicit-opt overload and pass fullInstall() to get reshape permission.
        this(rule, service, null, null, StorageManipulationOpt.createIfAbsent());
    }

    public MetricConvert(final MetricRuleConfig rule, final MeterSystem service,
                         final javassist.ClassPool pool,
                         final ClassLoader targetClassLoader) {
        this(rule, service, pool, targetClassLoader,
             StorageManipulationOpt.createIfAbsent());
    }

    /**
     * Runtime-rule overload carrying per-file classloader + storage policy.
     *
     * @param rule the MAL rule config to compile
     * @param service MeterSystem target for registration
     * @param pool per-file Javassist pool, or null to use the shared default
     * @param targetClassLoader per-file ClassLoader, or null to use the shared default
     * @param storageOpt policy for backend-side install; main-node passes fullInstall,
     *                   peer-node passes localCacheOnly to skip server DDL
     */
    public MetricConvert(final MetricRuleConfig rule, final MeterSystem service,
                         final javassist.ClassPool pool,
                         final ClassLoader targetClassLoader,
                         final StorageManipulationOpt storageOpt) {
        Preconditions.checkState(!Strings.isNullOrEmpty(rule.getMetricPrefix()));
        final String sourceName = rule.getSourceName();
        final FilterExpression filter = buildFilter(rule, pool, targetClassLoader);
        final List<? extends MetricRuleConfig.RuleConfig> rules = rule.getMetricsRules();

        // Two-phase apply at file granularity so a compile error on a later rule never
        // leaves earlier rules with measures already provisioned on the storage backend.
        //
        // Phase 1 — prepare every Analyzer: runs DSL.parse (Javassist codegen into the
        //   per-file ClassLoader when running on the runtime-rule path) + metadata
        //   extraction, but does NOT call MeterSystem.create. On any failure, the whole
        //   file apply aborts before any DDL fires; partial Javassist classes die with
        //   the (throwaway) per-file loader.
        //
        // Phase 2 — register: walks the prepared list and calls Analyzer.register which
        //   drives MeterSystem.create → StorageModels.add → per-backend listener DDL.
        //   On partial register failure the caller (MalFileApplier / Reconciler) rolls
        //   back only the metrics that this apply attempt actually created. Phase 2
        //   failures are rare in practice — MeterSystem.create is idempotent for same-
        //   shape re-registration and the runtime-rule path pre-removes shape-break
        //   metrics before reaching here.
        final List<Analyzer> prepared = IntStream.range(0, rules.size()).mapToObj(
            i -> {
                final MetricRuleConfig.RuleConfig r = rules.get(i);
                final String yamlSource = sourceName != null
                    ? sourceName + ".yaml:" + i : null;
                return prepareAnalyzer(
                    formatMetricName(rule, r.getName()),
                    filter,
                    formatExp(rule.getExpPrefix(), rule.getExpSuffix(), r.getExp()),
                    service,
                    yamlSource,
                    pool,
                    targetClassLoader,
                    storageOpt
                );
            }
        ).collect(toList());
        // Phase 2 — register. Track each metric name as it's successfully registered so a
        // mid-phase throw gives the caller an accurate "actually registered" set. The previous
        // design left the caller using the full enumerated metric list for rollback, which was
        // catastrophic for FILTER_ONLY edits: a compile surprise between register() calls would
        // wipe the old bundle's metrics that this apply attempt never touched.
        final Set<String> registered = new LinkedHashSet<>(prepared.size());
        for (final Analyzer a : prepared) {
            try {
                a.register();
            } catch (final Throwable t) {
                throw new PartialRegistrationException(
                    "phase-2 register failed for " + a.getMetricName(),
                    t, Collections.unmodifiableSet(new LinkedHashSet<>(registered)));
            }
            registered.add(a.getMetricName());
        }
        this.analyzers = prepared;
        this.registeredMetricNames = Collections.unmodifiableSet(registered);
    }

    /**
     * Metric names that completed phase-2 register on this instance — the set the caller would
     * unregister to undo a successful apply. Same as {@code analyzers.stream().map(getMetricName)}
     * for a fully-constructed instance; the field exists so {@link PartialRegistrationException}
     * can carry the same value for the partial case.
     */
    @Getter
    private final Set<String> registeredMetricNames;

    /**
     * Thrown from the ctor when phase-2 register throws after at least one metric was already
     * registered. Carries the subset that did land, so the caller can unregister exactly what
     * this apply attempt touched and leave the old bundle's unchanged metrics alone.
     *
     * <p>Phase-1 (compile) failures do NOT use this exception — nothing was registered, the
     * original Throwable propagates unwrapped.
     */
    public static final class PartialRegistrationException extends RuntimeException {
        @Getter
        private final Set<String> registeredBeforeFailure;

        public PartialRegistrationException(final String message, final Throwable cause,
                                             final Set<String> registeredBeforeFailure) {
            super(message, cause);
            this.registeredBeforeFailure = registeredBeforeFailure == null
                ? Collections.emptySet()
                : registeredBeforeFailure;
        }
    }

    Analyzer buildAnalyzer(final String metricsName,
                           final FilterExpression filter,
                           final String exp,
                           final MeterSystem service,
                           final String yamlSource) {
        return buildAnalyzer(metricsName, filter, exp, service, yamlSource, null, null);
    }

    Analyzer buildAnalyzer(final String metricsName,
                           final FilterExpression filter,
                           final String exp,
                           final MeterSystem service,
                           final String yamlSource,
                           final javassist.ClassPool pool,
                           final ClassLoader targetClassLoader) {
        return Analyzer.build(
            metricsName,
            filter,
            exp,
            service,
            yamlSource,
            pool,
            targetClassLoader
        );
    }

    /**
     * Compile-only counterpart to {@link #buildAnalyzer}. The ctor uses this in phase 1 so
     * every rule's MAL expression is parsed + typed before any {@code MeterSystem.create}
     * call fires. Phase 2 runs {@link Analyzer#register} on the returned objects.
     */
    Analyzer prepareAnalyzer(final String metricsName,
                              final FilterExpression filter,
                              final String exp,
                              final MeterSystem service,
                              final String yamlSource,
                              final javassist.ClassPool pool,
                              final ClassLoader targetClassLoader,
                              final StorageManipulationOpt storageOpt) {
        return Analyzer.prepare(
            metricsName,
            filter,
            exp,
            service,
            yamlSource,
            pool,
            targetClassLoader,
            storageOpt
        );
    }

    private static FilterExpression buildFilter(final MetricRuleConfig rule,
                                                final javassist.ClassPool pool,
                                                final ClassLoader targetClassLoader) {
        final String filterText = rule.getFilter();
        if (Strings.isNullOrEmpty(filterText)) {
            return null;
        }
        final String sourceName = rule.getSourceName();
        final String yamlSource = sourceName != null
            ? sourceName + ".yaml" : null;
        return new FilterExpression(filterText, "filter", yamlSource, pool, targetClassLoader);
    }

    private String formatExp(final String expPrefix, String expSuffix, String exp) {
        String ret = exp;
        if (!Strings.isNullOrEmpty(expPrefix)) {
            ret = String.format("(%s.%s)", StringUtils.substringBefore(exp, "."), expPrefix);
            final String after = StringUtils.substringAfter(exp, ".");
            if (!Strings.isNullOrEmpty(after)) {
                ret = String.format("(%s.%s)", ret, after);
            }
        }
        if (!Strings.isNullOrEmpty(expSuffix)) {
            ret = String.format("(%s).%s", ret, expSuffix);
        }
        return ret;
    }

    /**
     * Broadcasts the full sample family map to every Analyzer in this config file.
     *
     * <p>The map contains ALL metrics from a single scrape batch keyed by Prometheus metric name
     * (e.g., "node_cpu_seconds_total", "node_memory_MemTotal_bytes", ...).
     * Each Analyzer selects only the entries it needs via O(1) HashMap lookups on
     * {@code this.samples} (derived from compile-time AST metadata).
     *
     * @param sampleFamilies all sample families from one scrape, keyed by metric name.
     */
    public void toMeter(final ImmutableMap<String, SampleFamily> sampleFamilies) {
        Preconditions.checkNotNull(sampleFamilies);
        if (sampleFamilies.size() < 1) {
            return;
        }
        for (Analyzer each : analyzers) {
            try {
                each.analyse(sampleFamilies);
            } catch (Throwable t) {
                log.error("Analyze {} error", each, t);
            }
        }
    }

    private String formatMetricName(MetricRuleConfig rule, String meterRuleName) {
        StringJoiner metricName = new StringJoiner("_");
        metricName.add(rule.getMetricPrefix()).add(meterRuleName);
        return metricName.toString();
    }
}
