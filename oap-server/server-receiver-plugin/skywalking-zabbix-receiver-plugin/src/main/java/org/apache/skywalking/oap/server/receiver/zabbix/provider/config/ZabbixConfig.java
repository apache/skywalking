package org.apache.skywalking.oap.server.receiver.zabbix.provider.config;

import lombok.Data;
import org.apache.skywalking.oap.meter.analyzer.MetricRuleConfig;

import java.util.List;

@Data
public class ZabbixConfig implements MetricRuleConfig {

    private String metricPrefix;
    private String expSuffix;
    private List<Entity> entities;
    private List<Metric> metrics;

    @Override
    public List<? extends RuleConfig> getMetricsRules() {
        return metrics;
    }

    @Data
    public static class Entity {
        private String service;
        private String instancePattern;
    }

    @Data
    public static class Metric implements RuleConfig {
        private String name;
        private List<String> keys;
        private String exp;
    }
}
