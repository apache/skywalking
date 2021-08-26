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
 */

package org.apache.skywalking.oap.server.analyzer.provider.trace;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.library.util.yaml.ClassFilterConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Objects.isNull;

@Slf4j
public class TraceSamplingPolicyWatcher extends ConfigChangeWatcher {
    private final AtomicReference<String> settingsString;
    private AtomicInteger sampleRate;
    private AtomicInteger slowTraceSegmentThreshold;

    private volatile Map<String, ServiceSampleConfig> serviceSampleRates = Collections.emptyMap();

    public TraceSamplingPolicyWatcher(String settingFile, ModuleProvider provider) {
        super(AnalyzerModule.NAME, provider, "traceSamplingPolicy");
        this.settingsString = new AtomicReference<>(Const.EMPTY_STRING);
        slowTraceSegmentThreshold = new AtomicInteger();
        sampleRate = new AtomicInteger();
        final SampleRateSetting defaultConfigs = parseFromFile(settingFile);
        log.info("Default configured trace-sample-policy: {}", defaultConfigs);
        onUpdatedDefaultConfig(defaultConfigs);
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            this.settingsString.set("");
            slowTraceSegmentThreshold.set(getDefaultSlowTraceSegmentThreshold());
            sampleRate.set(getDefaultSampleRate());
            serviceSampleRates = Collections.emptyMap();
        } else {
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return settingsString.get();
    }

    public ServiceSampleConfig getSample(String serviceName) {
        return serviceSampleRates.get(serviceName);
    }

    public int getSampleRate() {
        return sampleRate.get();
    }

    public int getSlowTraceSegmentThreshold() {
        return slowTraceSegmentThreshold.get();
    }

    public boolean shouldSample(int duration) {
        return (slowTraceSegmentThreshold.get() > -1) && (duration >= slowTraceSegmentThreshold.get());
    }

    /**
     * Been updated when this object been init
     */
    private void onUpdatedDefaultConfig(SampleRateSetting defaultConfigs) {
        if (isNull(defaultConfigs)) {
            slowTraceSegmentThreshold.set(getDefaultSlowTraceSegmentThreshold());
            sampleRate.set(getDefaultSampleRate());
        } else {
            onUpdated(defaultConfigs);
        }
    }

    private void activeSetting(String config) {
        if (log.isDebugEnabled()) {
            log.debug("[trace-sample-policy] Updating using new config: {}", config);
        }
        // if parse failed, retain last configuration
        onUpdated(parseFromYml(config));
    }

    private void onUpdated(final SampleRateSetting sampleRateSetting) {
        log.info("Updating trace-sample-policy with: {}", sampleRateSetting);
        if (!isNull(sampleRateSetting)) {
            serviceSampleRates = StreamSupport.stream(sampleRateSetting.spliterator(), false)
                    .collect(Collectors.toMap(ServiceSampleConfig::getName, Function.identity()));

            if (sampleRateIsNotNull(sampleRateSetting)) {
                sampleRate.set(sampleRateSetting.defaults.rate.get());
            } else {
                sampleRate.set(getDefaultSampleRate());
            }

            if (slowTraceSegmentThresholdIsNotNull(sampleRateSetting)) {
                slowTraceSegmentThreshold.set(sampleRateSetting.defaults.duration.get());
            } else {
                slowTraceSegmentThreshold.set(getDefaultSlowTraceSegmentThreshold());
            }
        } else {
            // if null, retain last configuration
        }
    }

    private boolean sampleRateIsNotNull(SampleRateSetting sampleRateSetting) {
        return sampleRateSetting.defaults != null && sampleRateSetting.defaults.rate != null;
    }

    private boolean slowTraceSegmentThresholdIsNotNull(SampleRateSetting sampleRateSetting) {
        return sampleRateSetting.defaults != null && sampleRateSetting.defaults.duration != null;
    }

    private int getDefaultSampleRate() {
        return ((AnalyzerModuleConfig) this.getProvider().createConfigBeanIfAbsent()).getSampleRate();
    }

    private int getDefaultSlowTraceSegmentThreshold() {
        return ((AnalyzerModuleConfig) this.getProvider().createConfigBeanIfAbsent()).getSlowTraceSegmentThreshold();
    }

    private SampleRateSetting parseFromFile(final String file) {
        try {
            final Reader reader = ResourceUtils.read(file);
            Map<String, Object> map = new Yaml(new ClassFilterConstructor(new Class[]{
                    Map.class})).loadAs(reader, Map.class);
            return mapToSettingObject(map);
        } catch (Exception e) {
            log.error("[trace-sample-policy] Cannot load configs from: {}", file, e);
        }
        return null;
    }

    private SampleRateSetting parseFromYml(final String ymlContent) {
        try {
            Map<String, Object> map = new Yaml(new ClassFilterConstructor(new Class[]{
                    Map.class})).loadAs(ymlContent, Map.class);
            SampleRateSetting setting = mapToSettingObject(map);
            this.settingsString.set(ymlContent);
            return setting;
        } catch (Exception e) {
            log.error("[trace-sample-policy] Failed to parse yml content: \n{}", ymlContent, e);
        }
        return null;
    }

    private SampleRateSetting mapToSettingObject(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        SampleRateSetting setting = new SampleRateSetting();
        DefaultSampleRateConfig defaultSampleRateConfig = new DefaultSampleRateConfig();
        // default
        Object defaultMapObject = map.get("default");
        if (defaultMapObject != null) {
            Map<String, Object> defaultMap = (Map<String, Object>) defaultMapObject;
            defaultSampleRateConfig.setRate(new AtomicInteger((Integer) defaultMap.getOrDefault("rate", null)));
            defaultSampleRateConfig.setDuration(new AtomicInteger((Integer) defaultMap.getOrDefault("duration", null)));
        }
        setting.setDefaults(defaultSampleRateConfig);
        // services
        List<ServiceSampleConfig> services = new ArrayList<>();
        Object servicesObject = map.get("services");
        if (servicesObject != null) {
            List<Map<String, Object>> serviceList = (List<Map<String, Object>>) servicesObject;
            serviceList.forEach(service -> {
                ServiceSampleConfig serviceSampleConfig = new ServiceSampleConfig();
                serviceSampleConfig.setName((String) service.get("name"));
                serviceSampleConfig.setRate(service.get("rate") == null ? null : new AtomicInteger((Integer) service.get("rate")));
                serviceSampleConfig.setDuration(service.get("duration") == null ? null : new AtomicInteger((Integer) service.get("duration")));
                services.add(serviceSampleConfig);
            });
        }
        setting.setServices(services);
        return setting;
    }

    @Getter
    @Setter
    @ToString
    public static class ServiceSampleConfig {
        private String name;
        private AtomicInteger rate;
        private AtomicInteger duration;
    }

    @ToString
    public static class SampleRateSetting implements Iterable<ServiceSampleConfig> {
        @Getter
        @Setter
        private DefaultSampleRateConfig defaults;
        @Getter
        @Setter
        private Collection<ServiceSampleConfig> services;

        SampleRateSetting() {
            services = new ArrayList<>();
        }

        @Override
        public Iterator<ServiceSampleConfig> iterator() {
            return services.iterator();
        }
    }

    @Getter
    @Setter
    @ToString
    public static class DefaultSampleRateConfig {
        private AtomicInteger rate;
        private AtomicInteger duration;
    }

}
