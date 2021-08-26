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
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.library.util.yaml.ClassFilterConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.isNull;

@Slf4j
public class TraceSamplingPolicyWatcher extends ConfigChangeWatcher {

    private final AtomicReference<String> settingsString;
    private final AtomicReference<SampleRateSetting> sampleRateSetting;
    private final AnalyzerModuleConfig moduleConfig;

    public TraceSamplingPolicyWatcher(AnalyzerModuleConfig moduleConfig, ModuleProvider provider) {
        super(AnalyzerModule.NAME, provider, "traceSamplingPolicy");
        this.settingsString = new AtomicReference<>(Const.EMPTY_STRING);
        this.moduleConfig = moduleConfig;
        SampleRateSetting rateSetting = parseFromFile(moduleConfig.getTraceSampleRateSettingFile());
        // If settingFile has a empty config, use the default settings in AnalyzerModuleConfig
        this.sampleRateSetting = new AtomicReference<>(rateSetting == null ? defaultSampleConfigSetting() : rateSetting);
        log.info("[trace-sample-policy] Default configured trace-sample-policy: {}", this.sampleRateSetting);
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            this.settingsString.set("");
            this.sampleRateSetting.set(defaultSampleConfigSetting());
            log.info("[trace-sample-policy] Delete trace-sample-policy,use default config: {}", this.sampleRateSetting);
        } else {
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return this.settingsString.get();
    }

    public SampleConfig getSample(String service) {
        return this.sampleRateSetting.get().get(service);
    }

    public int getSampleRate() {
        return this.sampleRateSetting.get().defaultRate;
    }

    public int getSlowTraceSegmentThreshold() {
        return this.sampleRateSetting.get().defaultDuration;
    }

    public boolean shouldSample(int duration) {
        return (this.sampleRateSetting.get().defaultDuration > -1) && (duration >= this.sampleRateSetting.get().defaultDuration);
    }

    private void activeSetting(String config) {
        if (log.isDebugEnabled()) {
            log.debug("[trace-sample-policy] Updating using new config: {}", config);
        }
        onUpdated(parseFromYml(config));
    }

    private void onUpdated(final SampleRateSetting sampleRateSetting) {
        if (!isNull(sampleRateSetting)) {
            this.sampleRateSetting.set(sampleRateSetting);
            log.info("[trace-sample-policy] Updating trace-sample-policy with: {}", sampleRateSetting);
        } else if (StringUtil.isBlank(this.settingsString.get())) {
            this.sampleRateSetting.set(defaultSampleConfigSetting());
            log.info("[trace-sample-policy] Trace-sample-policy been set empty string,use default config: {}", this.sampleRateSetting);
        } else {
            log.info("[trace-sample-policy] Parse yaml fail, retain last configuration: {}", this.sampleRateSetting);
        }
    }

    private int getDefaultSampleRate() {
        return this.moduleConfig.getSampleRate();
    }

    private int getDefaultSlowTraceSegmentThreshold() {
        return this.moduleConfig.getSlowTraceSegmentThreshold();
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
        // It must have a default config on init
        return defaultSampleConfigSetting();
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
        // Config update maybe parse fail
        return null;
    }

    private SampleRateSetting mapToSettingObject(Map<String, Object> map) {
        // YmlContent maybe empty
        if (map == null) {
            return null;
        }
        SampleRateSetting setting;
        // default config
        Object defaultMapObject = map.get("default");
        if (defaultMapObject != null) {
            Map<String, Object> defaultMap = (Map<String, Object>) defaultMapObject;
            setting = new SampleRateSetting(defaultMap.get("rate") == null ? getDefaultSampleRate() : (Integer) defaultMap.get("rate"),
                    defaultMap.get("duration") == null ? getDefaultSlowTraceSegmentThreshold() : (Integer) defaultMap.get("duration"));
        } else {
            setting = defaultSampleConfigSetting();
        }
        // services config
        Object servicesObject = map.get("services");
        if (servicesObject != null) {
            List<Map<String, Object>> serviceList = (List<Map<String, Object>>) servicesObject;
            serviceList.forEach(service -> {
                String name = (String) service.get("name");
                if (StringUtil.isBlank(name)) {
                    return;
                }
                SampleConfig serviceSampleConfig = new SampleConfig();
                serviceSampleConfig.setRate(service.get("rate") == null ? null : (Integer) service.get("rate"));
                serviceSampleConfig.setDuration(service.get("duration") == null ? null : (Integer) service.get("duration"));
                setting.add(name, serviceSampleConfig);
            });
        }
        return setting;
    }

    private SampleRateSetting defaultSampleConfigSetting() {
        return new SampleRateSetting(getDefaultSampleRate(), getDefaultSlowTraceSegmentThreshold());
    }

    @Getter
    @Setter
    @ToString
    public static class SampleConfig {
        private Integer rate;
        private Integer duration;
    }

    @ToString
    public static class SampleRateSetting {
        @Getter
        @Setter
        private int defaultRate;
        @Getter
        @Setter
        private int defaultDuration;

        @Getter
        private Map<String, SampleConfig> services;

        SampleRateSetting(Integer defaultRate, Integer defaultDuration) {
            this.defaultRate = defaultRate;
            this.defaultDuration = defaultDuration;
            this.services = new ConcurrentHashMap<>();
        }

        public void add(String service, SampleConfig sampleConfig) {
            this.services.put(service, sampleConfig);
        }

        public SampleConfig get(String service) {
            return this.services.get(service);
        }
    }

}
