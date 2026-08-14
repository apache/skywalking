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

package org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.NoArgsConstructor;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricRuleConfig;
import org.apache.skywalking.oap.server.core.analysis.LayerDefinition;

import java.util.List;

/**
 * Rule contains the global configuration of prometheus fetcher.
 */
@Data
@NoArgsConstructor
public class Rule implements MetricRuleConfig {
    private String name;
    private String metricPrefix;
    private String expSuffix;
    private String expPrefix;
    private String filter;
    private List<MetricsRule> metricsRules;
    /**
     * Optional inline layer registrations. When present, each entry is registered through
     * {@code Layer.register(...)} before the rule's expressions compile, so the
     * rule file is self-describing for any custom layers it references.
     */
    private List<LayerDefinition> layerDefinitions;
    /**
     * File-level source anchors, stamped by the loader from {@code DslYamlLineIndex} — not
     * YAML-bound keys. Excluded from equals/hashCode/toString for the same reason as
     * {@link MetricsRule}'s: a rule file that merely shifted must not read as content-changed.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private int filterLine;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private int expPrefixLine;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private int expSuffixLine;

    /**
     * Full path of the rule file relative to the config root, e.g.
     * {@code otel-rules/activemq/activemq-broker.yaml}.
     *
     * <p>Distinct from {@link #getSourceName()}, which drops the ruleset directory and the
     * extension because it doubles as the rule's identity. This one is for humans: it is what a
     * stack frame shows and what an operator opens, so it must be complete and paste-able.
     */
    @ToString.Exclude
    private String sourcePath;

    /**
     * Explicit because {@code @Data} would generate a getter that SHADOWS
     * {@link MetricRuleConfig#getSourcePath()} and returns null for any loader that does not
     * stamp the field — the runtime-rule path parses a Rule straight from YAML
     * ({@code MalFileApplier.parse}), which would then produce {@code (null:32)null_L32_x.java}.
     * Falling back to the source name keeps that path usable even without the ruleset directory.
     *
     * @return the rule file path, never null once a name is set
     */
    @Override
    public String getSourcePath() {
        if (sourcePath != null) {
            return sourcePath;
        }
        return name == null ? null : name + ".yaml";
    }

    @Override
    public String getSourceName() {
        return name;
    }
}
