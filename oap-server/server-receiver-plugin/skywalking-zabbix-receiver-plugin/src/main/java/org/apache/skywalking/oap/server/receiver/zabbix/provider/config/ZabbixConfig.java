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
import org.apache.skywalking.oap.meter.analyzer.MetricRuleConfig;

import java.util.List;

@Data
public class ZabbixConfig implements MetricRuleConfig {

    private String metricPrefix;
    private String expSuffix;
    private Entities entities;
    private List<String> requiredZabbixItemKeys;
    private List<Metric> metrics;

    @Override
    public List<? extends RuleConfig> getMetricsRules() {
        return metrics;
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
        private String name;
        private String exp;
    }
}
