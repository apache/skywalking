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

package org.apache.skywalking.oap.server.receiver.zabbix.provider.config;

import lombok.Data;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricRuleConfig;

import java.util.List;

@Data
public class ZabbixConfig implements MetricRuleConfig {

    /** Rule file this config was loaded from, e.g. {@code zabbix-rules/agent.yaml}. */
    private String sourcePath;

    /** Rule identity: the file name without directory or extension. */
    private String sourceName;

    private String metricPrefix;
    private String expSuffix;
    private String expPrefix;
    private String filter;
    private Entities entities;
    private List<String> requiredZabbixItemKeys;
    private List<Metric> metrics;

    @Override
    public List<? extends RuleConfig> getMetricsRules() {
        return metrics;
    }

    /**
     * Explicit because {@code @Data} generates a getter that SHADOWS
     * {@link MetricRuleConfig#getSourcePath()}, so the interface's fallback stops running.
     *
     * <p>Harmless today — {@code ZabbixConfigs} is the only loader and stamps both fields — but
     * {@code Rule} already shipped this exact bug, and the only test that would catch a second
     * loader forgetting to stamp is a loader test that a second loader would not have.
     *
     * @return the stamped path, or the source name with a YAML extension
     */
    @Override
    public String getSourcePath() {
        if (sourcePath != null) {
            return sourcePath;
        }
        return sourceName == null ? null : sourceName + ".yaml";
    }

    @Data
    public static class Entities {
        private List<String> hostPatterns;
        private List<EntityLabel> labels;
    }

    @Data
    public static class EntityLabel {
        private String name;
        private String fromItem;
        private String value;
    }

    @Data
    public static class Metric implements RuleConfig {

        /** 1-based line of this metric's entry in the source YAML; 0 when unresolved. */
        private int lineNo;

        private String name;
        private String exp;
    }
}
