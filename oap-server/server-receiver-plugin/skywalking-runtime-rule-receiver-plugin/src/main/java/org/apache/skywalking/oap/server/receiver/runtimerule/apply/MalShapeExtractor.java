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

package org.apache.skywalking.oap.server.receiver.runtimerule.apply;

import com.google.common.base.Strings;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CaseUtils;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.DSL;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata;
import org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule.MetricsRule;
import org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule.Rule;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.yaml.snakeyaml.Yaml;

/**
 * Extracts the per-metric storage shape {@code (functionName, scopeType)} from a MAL rule file
 * without running Javassist codegen. The classifier uses this to decide whether an update is
 * truly STRUCTURAL (shape moved for at least one metric) or can ride the FILTER_ONLY fast path
 * (all shapes identical, only expression bodies / filters / tags changed).
 *
 * <p>Algorithm mirrors what {@code MetricConvert} + {@code Analyzer.init} do at apply time:
 * <ol>
 *   <li>Apply {@code expPrefix} / {@code expSuffix} to the rule's {@code exp} field exactly
 *       as {@code MetricConvert.formatExp} does, producing the final expression string.</li>
 *   <li>Parse that string via {@link DSL#extractMetadata(String)} (AST walk only, no Javassist
 *       bytecode generation — cheap enough to run on every classify call).</li>
 *   <li>Derive the storage function name by the same formula {@code Analyzer.init} uses:
 *       {@code CaseUtils.toCamelCase(downsampling.lowercase) + capitalize(dataType)}, where
 *       dataType is picked from {@code isHistogram} + {@code percentiles} + {@code labels}
 *       exactly as {@code Analyzer.MetricType} does.</li>
 *   <li>Pair with the scope type directly from the metadata.</li>
 * </ol>
 *
 * <p>Same-shape = same storage-side class = no {@code MeterSystem.removeMetric} + no BanyanDB
 * {@code deleteMeasure}. Different shape = the design's "shape-break" case — every shipped
 * backend treats the Metrics subclass identity as the measure/table identity, and swapping
 * function or scope moves that identity. The runtime-rule {@code allowStorageChange} guardrail
 * uses this set to flag when an operator is about to drop an existing measure's data.
 */
public final class MalShapeExtractor {

    private MalShapeExtractor() {
    }

    /**
     * Per-metric storage shape. Equality / hash code deliberately over both fields so the
     * classifier can diff shape maps with a straight {@code Map.equals}-style comparison.
     */
    public static final class MalShape {
        private final String functionName;
        private final ScopeType scopeType;

        public MalShape(final String functionName, final ScopeType scopeType) {
            this.functionName = functionName;
            this.scopeType = scopeType;
        }

        public String getFunctionName() {
            return functionName;
        }

        public ScopeType getScopeType() {
            return scopeType;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MalShape)) {
                return false;
            }
            final MalShape other = (MalShape) o;
            return Objects.equals(functionName, other.functionName)
                && scopeType == other.scopeType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(functionName, scopeType);
        }

        @Override
        public String toString() {
            return "(" + functionName + "," + scopeType + ")";
        }
    }

    /**
     * Parse a MAL YAML file and return a map {@code metricName → shape}, where metric names
     * follow the same {@code metricPrefix + "_" + ruleName} formula {@code MetricConvert} uses.
     *
     * <p>Returns an empty map when the YAML is null/empty or has no {@code metricsRules}. Any
     * rule whose expression fails to parse is dropped from the result — the classifier treats
     * "missing shape" conservatively (falls back to the STRUCTURAL-with-over-approximation
     * path it already has).
     */
    public static Map<String, MalShape> extract(final String yamlContent) {
        if (yamlContent == null || yamlContent.isEmpty()) {
            return Collections.emptyMap();
        }
        final Rule rule;
        try (StringReader r = new StringReader(yamlContent)) {
            rule = new Yaml().loadAs(r, Rule.class);
        } catch (final Throwable t) {
            throw new IllegalArgumentException("MAL YAML parse failure: " + t.getMessage(), t);
        }
        if (rule == null || rule.getMetricsRules() == null || rule.getMetricPrefix() == null) {
            return Collections.emptyMap();
        }
        final Map<String, MalShape> out = new LinkedHashMap<>();
        for (final MetricsRule mr : rule.getMetricsRules()) {
            if (mr.getName() == null) {
                continue;
            }
            final String metricName = rule.getMetricPrefix() + "_" + mr.getName();
            final String fullExpr = formatExp(rule.getExpPrefix(), rule.getExpSuffix(), mr.getExp());
            final MalShape shape = extractShape(fullExpr);
            if (shape != null) {
                out.put(metricName, shape);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Extract shape from a single pre-assembled MAL expression string. Returns {@code null}
     * when the parser fails — caller treats that as "unknown shape" and falls back to the
     * conservative classifier behaviour. Swallowing the parse error here is deliberate:
     * classification is advisory metadata layered on top of the actual apply, which will
     * raise its own compile-error if the YAML is broken.
     */
    public static MalShape extractShape(final String fullExpression) {
        if (Strings.isNullOrEmpty(fullExpression)) {
            return null;
        }
        try {
            final ExpressionMetadata md = DSL.extractMetadata(fullExpression);
            final String dataType = chooseDataType(md);
            final String downSamplingStr =
                CaseUtils.toCamelCase(md.getDownsampling().toString().toLowerCase(), false, '_');
            final String functionName = String.format("%s%s",
                downSamplingStr, StringUtils.capitalize(dataType));
            return new MalShape(functionName, md.getScopeType());
        } catch (final Throwable t) {
            return null;
        }
    }

    /**
     * Replicates {@code MetricConvert.formatExp(expPrefix, expSuffix, exp)} so classifier-time
     * shape extraction sees the exact same expression string {@code Analyzer.build} would
     * compile. Keeping the logic here — rather than exposing it from {@code MetricConvert} —
     * avoids coupling the two modules' APIs around a single five-line string operation.
     */
    private static String formatExp(final String expPrefix, final String expSuffix, final String exp) {
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
     * Mirror of {@code Analyzer.init}'s MetricType resolution:
     * histogram → "histogram" (unless percentiles are specified, then "histogramPercentile");
     * labels present → "labeled"; otherwise → "" (single).
     */
    private static String chooseDataType(final ExpressionMetadata md) {
        if (md.isHistogram()) {
            if (md.getPercentiles() != null && md.getPercentiles().length > 0) {
                return "histogramPercentile";
            }
            return "histogram";
        }
        if (!md.getLabels().isEmpty()) {
            return "labeled";
        }
        return "";
    }
}
