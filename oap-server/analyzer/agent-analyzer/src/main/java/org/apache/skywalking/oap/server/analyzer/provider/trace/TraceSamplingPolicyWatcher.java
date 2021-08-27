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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

    private final AtomicReference<String> settingsString = new AtomicReference<>(Const.EMPTY_STRING);
    private final AtomicReference<SamplePolicySettings> samplePolicySettings = new AtomicReference<>(null);
    private final AnalyzerModuleConfig moduleConfig;

    public TraceSamplingPolicyWatcher(AnalyzerModuleConfig moduleConfig, ModuleProvider provider) {
        super(AnalyzerModule.NAME, provider, "traceSamplingPolicy");
        this.moduleConfig = moduleConfig;
        loadDefaultPolicy();
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            this.settingsString.set("");
            log.info("[trace-sampling-policy] Delete trace-sample-policy,use default config");
            loadDefaultPolicy();
        } else {
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return this.settingsString.get();
    }

    public SamplePolicy getSamplePolicy(String service) {
        return this.samplePolicySettings.get().getPolicy(service);
    }

    public int getSampleRate() {
        return this.samplePolicySettings.get().global.rate;
    }

    public int getSlowTraceSegmentThreshold() {
        return this.samplePolicySettings.get().global.duration;
    }

    public boolean shouldSample(int duration) {
        return (this.samplePolicySettings.get().global.duration > -1) && (duration >= this.samplePolicySettings.get().global.duration);
    }

    private void loadDefaultPolicy() {
        SamplePolicySettings rateSetting = parseFromFile(moduleConfig.getTraceSamplingPolicySettingsFile());
        // If settingFile has a empty config, use the default settings
        this.samplePolicySettings.set(rateSetting == null ? defaultSampleConfigSettings() : rateSetting);
        log.info("[trace-sampling-policy] Default configured trace-sample-policy: {}", this.samplePolicySettings);
    }

    private void activeSetting(String config) {
        if (log.isDebugEnabled()) {
            log.debug("[trace-sampling-policy] Updating using new config: {}", config);
        }
        onUpdated(parseFromYml(config));
    }

    private void onUpdated(final SamplePolicySettings samplePolicySettings) {
        if (!isNull(samplePolicySettings)) {
            this.samplePolicySettings.set(samplePolicySettings);
            log.info("[trace-sampling-policy] Updating trace-sample-policy with: {}", samplePolicySettings);
        } else if (StringUtil.isBlank(this.settingsString.get())) {
            log.info("[trace-sampling-policy] Trace-sample-policy been set empty string,use default config");
            loadDefaultPolicy();
        } else {
            log.info("[trace-sampling-policy] Parse yaml fail, retain last configuration: {}", this.samplePolicySettings);
        }
    }

    private SamplePolicySettings parseFromFile(final String file) {
        try {
            final Reader reader = ResourceUtils.read(file);
            Map<String, Object> map = new Yaml(new ClassFilterConstructor(new Class[]{
                    Map.class})).loadAs(reader, Map.class);
            return mapToSettingObject(map);
        } catch (Exception e) {
            log.error("[trace-sampling-policy] Cannot load configs from: {}", file, e);
        }
        // It must have a default config on init
        return defaultSampleConfigSettings();
    }

    private SamplePolicySettings parseFromYml(final String ymlContent) {
        try {
            Map<String, Object> map = new Yaml(new ClassFilterConstructor(new Class[]{
                    Map.class})).loadAs(ymlContent, Map.class);
            SamplePolicySettings setting = mapToSettingObject(map);
            this.settingsString.set(ymlContent);
            return setting;
        } catch (Exception e) {
            log.error("[trace-sampling-policy] Failed to parse yml content: \n{}", ymlContent, e);
        }
        // Config update maybe parse fail
        return null;
    }

    private SamplePolicySettings mapToSettingObject(Map<String, Object> map) {
        // YmlContent maybe empty
        if (map == null) {
            return null;
        }
        SamplePolicySettings settings;
        // default config
        Object defaultMapObject = map.get("default");
        if (defaultMapObject != null) {
            Map<String, Object> defaultMap = (Map<String, Object>) defaultMapObject;
            settings = new SamplePolicySettings(defaultMap.get("rate") == null ? null : (Integer) defaultMap.get("rate"),
                    defaultMap.get("duration") == null ? null : (Integer) defaultMap.get("duration"));
        } else {
            settings = defaultSampleConfigSettings();
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
                SamplePolicy samplePolicy = new SamplePolicy();
                samplePolicy.setRate(service.get("rate") == null ? null : (Integer) service.get("rate"));
                samplePolicy.setDuration(service.get("duration") == null ? null : (Integer) service.get("duration"));
                settings.addPolicy(name, samplePolicy);
            });
        }
        return settings;
    }

    private SamplePolicySettings defaultSampleConfigSettings() {
        return new SamplePolicySettings(null, null);
    }

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SamplePolicy {
        private Integer rate;
        private Integer duration;
    }

    @ToString
    public static class SamplePolicySettings {

        @Getter
        // `default` is the keyword ,so named `global`
        private SamplePolicy global;
        @Getter
        private Map<String, SamplePolicy> services;

        /**
         * @param defaultRate     The sample rate precision is 1/10000. 10000 means 100% sample in default.
         * @param defaultDuration Setting this threshold about the latency would make the slow trace segments sampled if they cost more time, even the sampling mechanism activated. The default value is `-1`, which means would not sample slow traces. Unit, millisecond.
         */
        SamplePolicySettings(Integer defaultRate, Integer defaultDuration) {
            SamplePolicy samplePolicy = new SamplePolicy(defaultRate == null ? 10000 : defaultRate, defaultDuration == null ? -1 : defaultDuration);
            this.global = samplePolicy;
            this.services = new ConcurrentHashMap<>();
        }

        public void addPolicy(String service, SamplePolicy samplePolicy) {
            this.services.put(service, samplePolicy);
        }

        public SamplePolicy getPolicy(String service) {
            return this.services.get(service);
        }
    }

}
