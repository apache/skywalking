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
import io.vavr.Tuple;
import io.vavr.Tuple2;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.text.StringTokenizer;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.config.ZabbixConfig;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean.ZabbixRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

/**
 * Management all Zabbix metrics
 */
@Slf4j
public class ZabbixMetrics {

    private final List<ZabbixConfig> originalConfigs;

    /**
     * All enabled service and instance group
     */
    private List<InstanceGroup> allServices = new ArrayList<>();

    public ZabbixMetrics(List<ZabbixConfig> originalConfigs, MeterSystem meterSystem) {
        this.originalConfigs = originalConfigs;
        initConfigs(meterSystem);
    }

    /**
     * Get all key names when Zabbix agent queried
     */
    public Set<String> getAllMonitorMetricNames(String hostName) {
        // Find instance group
        return findInstanceGroup(hostName).map(InstanceGroup::getEnabledKeys).orElse(null);
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
            .map(e -> findInstanceGroup(e.getKey()).map(instanceGroup -> instanceGroup.convertToMeter(e.getValue())).orElse(null))
            .filter(Objects::nonNull)
            // Merge all statics
            .reduce(ConvertStatics::merge)
            .orElse(ConvertStatics.EMPTY);
    }

    private Optional<InstanceGroup> findInstanceGroup(String hostName) {
        // Find service group, support using cache
        return allServices.stream().filter(group -> group.matchesWithHostName(hostName)).findAny();
    }

    private void initConfigs(MeterSystem meterSystem) {
        // Temporary instance group cache, encase generate multiple instance group
        HashMap<String, InstanceGroup> tmpGroupCache = new HashMap<>();

        // Each config entities
        originalConfigs.forEach(c ->
            c.getEntities().getHostPatterns().forEach(instance ->
                tmpGroupCache.computeIfAbsent(instance, ins -> {
                    InstanceGroup instanceGroup = new InstanceGroup(ins, meterSystem);
                    allServices.add(instanceGroup);
                    return instanceGroup;
                }).appendMetrics(c)));
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
     * The group of instances according to hostPatterns defined in Zabbix rule file
     */
    private static class InstanceGroup {
        static final InstanceGroup EMPTY = new InstanceGroup("", null);

        private final Pattern instancePattern;
        private final MeterSystem meterSystem;
        @Getter
        private Set<String> enabledKeys;
        private List<MetricConvert> metricConverts;
        private List<ZabbixConfig.EntityLabel> labels;

        public InstanceGroup(String instancePattern, MeterSystem meterSystem) {
            this.instancePattern = Pattern.compile(instancePattern);
            this.meterSystem = meterSystem;
            this.enabledKeys = new HashSet<>();
            this.metricConverts = new ArrayList<>();
            this.labels = new ArrayList<>();
        }

        public void appendMetrics(ZabbixConfig config) {
            // Append metrics to converters
            metricConverts.add(new MetricConvert(config, meterSystem));

            // Append labels and add to item keys
            if (CollectionUtils.isNotEmpty(config.getEntities().getLabels())) {
                labels.addAll(config.getEntities().getLabels());

                config.getEntities().getLabels().stream().filter(l -> StringUtils.isNotBlank(l.getFromItem()))
                    .forEach(l -> enabledKeys.add(l.getFromItem()));
            }

            // Append all metric keys
            enabledKeys.addAll(config.getRequiredZabbixItemKeys());
        }

        public boolean matchesWithHostName(String hostName) {
            Matcher matcher = instancePattern.matcher(hostName);
            return matcher.matches();
        }

        public ConvertStatics convertToMeter(List<ZabbixRequest.AgentData> dataList) {
            if (log.isDebugEnabled()) {
                log.debug("Receive zabbix agent data: {}", dataList);
            }
            StopWatch stopWatch = new StopWatch();
            Collection<SampleFamily> sampleFamilies = null;
            try {
                stopWatch.start();

                // Parse config labels
                Map<String, String> configLabels = parseConfigLabels(dataList);

                // Build metrics
                ImmutableMap<String, SampleFamily> families = dataList.stream()
                    // Correct state
                    .filter(d -> d.getState() == 0 && NumberUtils.isParsable(d.getValue()))
                    // Parse data to list
                    .map(this::parseAgentData)
                    .map(b -> b.build(configLabels))
                    // Combine to sample family
                    .collect(Collectors.groupingBy(Sample::getName))
                    .entrySet().stream().collect(toImmutableMap(
                        Map.Entry::getKey,
                        e -> SampleFamilyBuilder.newBuilder(e.getValue().stream().toArray(Sample[]::new)).build()));

                sampleFamilies = families.values();

                // Each all converters
                metricConverts.forEach(converter -> converter.toMeter(families));
            } finally {
                stopWatch.stop();
            }

            return ConvertStatics.builder()
                .total(sampleFamilies.size())
                // Setting all as success
                .success(sampleFamilies.size())
                .useTime(((double) stopWatch.getTime()) / 1000)
                .build();
        }

        /**
         * Parsing config labels from original value or agent data
         */
        private Map<String, String> parseConfigLabels(List<ZabbixRequest.AgentData> dataList) {
            if (CollectionUtils.isEmpty(labels)) {
                return Collections.emptyMap();
            }

            return labels.stream().map(label -> {
                // Exists Value
                if (StringUtil.isNotBlank(label.getValue())) {
                    return Tuple.of(label.getName(), label.getValue());
                } else if (StringUtil.isNotBlank(label.getFromItem())) {
                    // Searching from Agent data
                    return dataList.stream()
                        .filter(d -> Objects.equals(d.getKey(), label.getFromItem())).findFirst()
                        .map(d -> Tuple.of(label.getName(), d.getValue())).orElse(null);
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));
        }

        /**
         * Parsing Zabbix agent data to sample builder
         */
        private SampleBuilder parseAgentData(ZabbixRequest.AgentData data) {
            String keyName = data.getKey();
            SampleBuilder.SampleBuilderBuilder builder = SampleBuilder.builder();

            if (keyName.contains("[") && keyName.endsWith("]")) {
                String key = StringUtils.substringBefore(keyName, "[");

                // Split params, support quote mode, label name start at 1
                StringTokenizer tokenizer = new StringTokenizer(
                    StringUtils.substringAfter(keyName.substring(0, keyName.length() - 1), "["), ',', '\"');
                tokenizer.setIgnoreEmptyTokens(false);
                int inx = 1;
                ImmutableMap.Builder<String, String> paramBuilder = ImmutableMap.builder();
                while (tokenizer.hasNext()) {
                    paramBuilder.put(String.valueOf(inx++), tokenizer.next());
                }

                builder.name(key).labels(paramBuilder.build());
            } else {
                builder.name(keyName).labels(ImmutableMap.of());
            }

            return builder.hostName(data.getHost())
                .timestamp(TimeUnit.SECONDS.toMillis(data.getClock()))
                .value(Double.parseDouble(data.getValue()))
                .build();
        }
    }

    @Builder
    @Data
    private static class SampleBuilder {

        private final String name;
        private final String hostName;
        private final long timestamp;
        private final ImmutableMap<String, String> labels;
        private final double value;

        public Sample build(Map<String, String> configLabels) {
            return Sample.builder()
                .name(escapedName(name))
                .labels(ImmutableMap.<String, String>builder()
                    // Put original labels
                    .putAll(labels)
                    // Put config labels
                    .putAll(configLabels)
                    // Put report instance to labels
                    .put("host", hostName)
                    .build())
                .value(value)
                .timestamp(timestamp).build();
        }

        // Returns the escaped name of the given one, with "." replaced by "_"
        private String escapedName(final String name) {
            return name.replaceAll("\\.", "_");
        }
    }

}
