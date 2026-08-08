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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.NoArgsConstructor;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricRuleConfig;

/**
 * MetricsRule holds the parsing expression.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsRule implements MetricRuleConfig.RuleConfig {
    private String name;
    private String exp;
    /**
     * Source anchors, stamped by the loader from {@code MalYamlLineIndex} — not YAML-bound keys.
     * Excluded from equals/hashCode/toString so a rule's identity stays its content: the
     * runtime-rule delta classifier compares parsed rules, and a rule that merely moved down the
     * file must not read as changed.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private int lineNo;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private int expLine;
}
