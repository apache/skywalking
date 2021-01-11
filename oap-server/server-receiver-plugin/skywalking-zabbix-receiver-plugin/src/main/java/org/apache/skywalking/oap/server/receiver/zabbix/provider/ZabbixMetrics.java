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

package org.apache.skywalking.oap.server.receiver.zabbix.provider;

import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.text.StringTokenizer;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.config.ZabbixConfig;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean.ZabbixRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Management all Zabbix metrics
 */
@Slf4j
public class ZabbixMetrics {

    private final List<ZabbixConfig> originalConfigs;

    /**
     * All enabled service and instance group
     */
    private List<ServiceGroup> allServices = new ArrayList<>();

    /**
     * Cache host name to service group, help to quick find service group when receive agent request
     */
    private Map<String, ServiceGroup> hostWithGroupCache = new ConcurrentHashMap<>();

    public ZabbixMetrics(List<ZabbixConfig> originalConfigs, MeterSystem meterSystem) {
        this.originalConfigs = originalConfigs;
        initConfigs(meterSystem);
    }

    /**
     * Get all key names when Zabbix agent queried
     */
    public Set<String> getAllMonitorMetricNames(String hostName) {
        // Find service group
        return findServiceGroup(hostName).map(ServiceGroup::getEnabledKeys).orElse(null);
    }

    /**
     * Receive agent data and convert to meter system
     */
    public ConvertStatics convertMetrics(List<ZabbixRequest.AgentData> agentDataList) {
        if (CollectionUtils.isEmpty(agentDataList)) {
            return ConvertStatics.EMPTY;
        }

        return agentDataList.stream()
            // Group by host
            .collect(Collectors.groupingBy(ZabbixRequest.AgentData::getHost)).entrySet().stream()
            // Convert every agent data list
            .map(e -> findServiceGroup(e.getKey()).map(serviceGroup -> serviceGroup.convertToMeter(e.getValue())).orElse(null))
            .filter(Objects::nonNull)
            // Merge all statics
            .reduce(ConvertStatics::merge)
            .orElse(ConvertStatics.EMPTY);
    }

    private Optional<ServiceGroup> findServiceGroup(String hostName) {
        // Find service group, support using cache
        return Optional.ofNullable(hostWithGroupCache.computeIfAbsent(hostName,
            host -> allServices.stream().filter(group -> group.matchesWithHostName(host)).findAny().orElse(null)));
    }

    private void initConfigs(MeterSystem meterSystem) {
        // Temporary service group cache, encase generate multiple service group
        HashMap<String, ServiceGroup> tmpGroupCache = new HashMap<>();

        // Each config entities
        originalConfigs.forEach(c ->
            c.getEntities().forEach(entity ->
                tmpGroupCache.computeIfAbsent(entity.getInstancePattern(), instance -> {
                    ServiceGroup serviceGroup = new ServiceGroup(instance, meterSystem);
                    allServices.add(serviceGroup);
                    return serviceGroup;
                }).appendMetricsToService(entity.getService(), c)));
    }

    /**
     * Metrics convert to meter system statics
     */
    @Builder
    @Getter
    public static class ConvertStatics {
        public static final ConvertStatics EMPTY = ConvertStatics.builder().build();
        private int total;
        private int success;
        private int failed;
        private double useTime;

        public ConvertStatics merge(ConvertStatics statics) {
            this.total += statics.total;
            this.success += statics.success;
            this.failed += statics.failed;
            this.useTime += statics.useTime;
            return this;
        }
    }

    /**
     * Service group metrics
     */
    private class ServiceGroup {
        private final Pattern paramSplitter = Pattern.compile("[^\\/]*\\,");
        private final Pattern instancePattern;
        private final MeterSystem meterSystem;
        @Getter
        private Set<String> enabledKeys;
        private Map<String, List<MetricConvert>> serviceMetricsConverters;

        public ServiceGroup(String instancePattern, MeterSystem meterSystem) {
            this.instancePattern = Pattern.compile(instancePattern);
            this.meterSystem = meterSystem;
            this.enabledKeys = new HashSet<>();
            this.serviceMetricsConverters = new HashMap<>();
        }

        public void appendMetricsToService(String serviceName, ZabbixConfig config) {
            // Append metrics to converters
            List<MetricConvert> converts = serviceMetricsConverters.computeIfAbsent(serviceName, service -> new ArrayList<>());
            converts.add(new MetricConvert(config, meterSystem));

            // Append all keys
            config.getMetrics().stream().flatMap(m -> m.getKeys().stream()).forEach(enabledKeys::add);
        }

        public boolean matchesWithHostName(String hostName) {
            Matcher matcher = instancePattern.matcher(hostName);
            return matcher.matches();
        }

        public ConvertStatics convertToMeter(List<ZabbixRequest.AgentData> dataList) {
            StopWatch stopWatch = new StopWatch();
            Map<String, List<SampleBuilder>> sampleFamilies = null;
            try {
                stopWatch.start();
                // Convert to SampleFamily
                sampleFamilies = dataList.stream()
                    // Correct state
                    .filter(d -> d.getState() == 0 && NumberUtils.isParsable(d.getValue()))
                    // Parse data to list
                    .map(this::parseAgentData)
                    // Combine to sample family
                    .collect(Collectors.groupingBy(SampleBuilder::getName));

                // Each all converters
                Map<String, List<SampleBuilder>> finalSampleFamilies = sampleFamilies;
                serviceMetricsConverters.entrySet().forEach(serviceEntry ->
                    serviceEntry.getValue().forEach(converter ->
                        // Build Samples and send to meter system
                        converter.toMeter(finalSampleFamilies.entrySet().stream()
                            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, entry ->
                                SampleFamilyBuilder.newBuilder(
                                    entry.getValue().stream().map(s -> s.build(serviceEntry.getKey())).toArray(Sample[]::new)
                                ).build())))
                ));
            } finally {
                stopWatch.stop();
            }

            return ConvertStatics.builder()
                .total(sampleFamilies.size())
                // Setting all as success
                .success(sampleFamilies.size())
                .useTime(((double)stopWatch.getTime()) / 1000)
                .build();
        }

        private SampleBuilder parseAgentData(ZabbixRequest.AgentData data) {
            String keyName = data.getKey();
            SampleBuilder.SampleBuilderBuilder builder = SampleBuilder.builder();

            if (keyName.contains("[") && keyName.endsWith("]")) {
                String key = StringUtils.substringBefore(keyName, "[");

                // Split params, support quote mode, label name start at 1
                StringTokenizer tokenizer = new StringTokenizer(
                    StringUtils.substringAfter(keyName.substring(0, keyName.length() - 1), "["), ',', '\"');
                int inx = 1;
                ImmutableMap.Builder<String, String> paramBuilder = ImmutableMap.builder();
                while (tokenizer.hasNext()) {
                    paramBuilder.put(String.valueOf(inx++), tokenizer.next());
                }

                builder.name(key).labels(paramBuilder.build());
            } else {
                builder.name(keyName).labels(ImmutableMap.of());
            }

            return builder.instanceName(data.getHost())
                .timestamp(TimeUnit.SECONDS.toMillis(data.getClock()))
                .value(Double.parseDouble(data.getValue()))
                .build();
        }
    }

    @Builder
    @Data
    private static class SampleBuilder {

        private final String name;
        private final String instanceName;
        private final long timestamp;
        private final ImmutableMap<String, String> labels;
        private final double value;

        public Sample build(String service) {
            return Sample.builder()
                .name(name)
                .labels(ImmutableMap.<String, String>builder()
                    // Put original labels
                    .putAll(labels)
                    // Put report service and instance to labels
                    .put("service", service)
                    .put("instance", instanceName)
                    .build())
                .value(value)
                .timestamp(timestamp).build();
        }
    }

}
