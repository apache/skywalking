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
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.FilterExpression;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;

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
        Preconditions.checkState(!Strings.isNullOrEmpty(rule.getMetricPrefix()));
        final String sourceName = rule.getSourceName();
        final FilterExpression filter = buildFilter(rule);
        final List<? extends MetricRuleConfig.RuleConfig> rules = rule.getMetricsRules();
        this.analyzers = IntStream.range(0, rules.size()).mapToObj(
            i -> {
                final MetricRuleConfig.RuleConfig r = rules.get(i);
                final String yamlSource = sourceName != null
                    ? sourceName + ".yaml:" + i : null;
                return buildAnalyzer(
                    formatMetricName(rule, r.getName()),
                    filter,
                    formatExp(rule.getExpPrefix(), rule.getExpSuffix(), r.getExp()),
                    service,
                    yamlSource
                );
            }
        ).collect(toList());
    }

    Analyzer buildAnalyzer(final String metricsName,
                           final FilterExpression filter,
                           final String exp,
                           final MeterSystem service,
                           final String yamlSource) {
        return Analyzer.build(
            metricsName,
            filter,
            exp,
            service,
            yamlSource
        );
    }

    private static FilterExpression buildFilter(final MetricRuleConfig rule) {
        final String filterText = rule.getFilter();
        if (Strings.isNullOrEmpty(filterText)) {
            return null;
        }
        final String sourceName = rule.getSourceName();
        final String yamlSource = sourceName != null
            ? sourceName + ".yaml" : null;
        return new FilterExpression(filterText, "filter", yamlSource);
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
