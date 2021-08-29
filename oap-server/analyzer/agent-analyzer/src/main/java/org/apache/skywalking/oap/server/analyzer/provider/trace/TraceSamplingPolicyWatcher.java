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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.sampling.SamplingPolicy;
import org.apache.skywalking.oap.server.analyzer.provider.trace.sampling.SamplingPolicySettings;
import org.apache.skywalking.oap.server.analyzer.provider.trace.sampling.SamplingPolicySettingsReader;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.isNull;

@Slf4j
public class TraceSamplingPolicyWatcher extends ConfigChangeWatcher {

    private final AtomicReference<String> settingsString = new AtomicReference<>(Const.EMPTY_STRING);
    private final AtomicReference<SamplingPolicySettings> samplingPolicySettings = new AtomicReference<>(null);
    private final SamplingPolicySettings defaultSamplingPolicySettings;

    public TraceSamplingPolicyWatcher(AnalyzerModuleConfig moduleConfig, ModuleProvider provider) {
        super(AnalyzerModule.NAME, provider, "traceSamplingPolicy");
        SamplingPolicySettings samplingPolicySettings = parseFromFile(moduleConfig.getTraceSamplingPolicySettingsFile());
        this.defaultSamplingPolicySettings = samplingPolicySettings;
        loadDefaultPolicySettings();
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType()) || StringUtil.isBlank(value.getNewValue())) {
            this.settingsString.set("");
            log.info("[trace-sampling-policy] Delete trace-sampling-policy,use default config");
            loadDefaultPolicySettings();
        } else {
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return this.settingsString.get();
    }

    public boolean shouldSample(String service, int sample, int duration) {
        SamplingPolicy samplingPolicy = this.samplingPolicySettings.get().get(service);
        if (samplingPolicy == null) {
            return shouldSampleGlobal(sample, duration);
        }
        return shouldSampleService(samplingPolicy, sample, duration);
    }

    public SamplingPolicy getSamplingPolicy(String service) {
        return this.samplingPolicySettings.get().get(service);
    }

    private boolean shouldSampleGlobal(int sample, int duration) {
        return isOverSlowThresholdGlobal(duration) || withinRateRangeGlobal(sample);
    }

    private boolean shouldSampleService(SamplingPolicy samplingPolicy, int sample, int duration) {
        return (samplingPolicy.getDuration() != null && isOverSlowThreshold(duration, samplingPolicy.getDuration()))
                || (samplingPolicy.getRate() != null && withinRateRange(sample, samplingPolicy.getRate()))
                // global policy
                || (samplingPolicy.getDuration() == null && isOverSlowThresholdGlobal(duration))
                || (samplingPolicy.getRate() == null && withinRateRangeGlobal(sample));
    }

    private boolean withinRateRangeGlobal(int sample) {
        return withinRateRange(sample, this.samplingPolicySettings.get().getGlobal().getRate());
    }

    private boolean isOverSlowThresholdGlobal(int duration) {
        return isOverSlowThreshold(duration, this.samplingPolicySettings.get().getGlobal().getDuration());
    }

    private boolean isOverSlowThreshold(int currentDuration, int policyDuration) {
        return (policyDuration > -1) && (currentDuration >= policyDuration);
    }

    private boolean withinRateRange(int currentSample, int policySample) {
        return currentSample < policySample;
    }

    private void loadDefaultPolicySettings() {
        this.samplingPolicySettings.set(defaultSamplingPolicySettings);
        log.info("[trace-sampling-policy] use trace-sample-policy in static file : {}", this.samplingPolicySettings);
    }

    private void activeSetting(String config) {
        if (log.isDebugEnabled()) {
            log.debug("[trace-sampling-policy] Updating using new config: {}", config);
        }
        onUpdated(parseFromYml(config));
    }

    private void onUpdated(final SamplingPolicySettings samplingPolicySettings) {
        if (!isNull(samplingPolicySettings)) {
            this.samplingPolicySettings.set(samplingPolicySettings);
            log.info("[trace-sampling-policy] Updating trace-sample-policy with: {}", samplingPolicySettings);
        } else {
            log.info("[trace-sampling-policy] Parse yaml fail, retain last configuration: {}", this.samplingPolicySettings);
        }
    }

    private SamplingPolicySettings parseFromFile(final String file) {
        try {
            SamplingPolicySettingsReader reader = new SamplingPolicySettingsReader(ResourceUtils.read(file));
            return reader.readSettings();
        } catch (Exception e) {
            log.error("[trace-sampling-policy] Cannot load configs from: {}", file, e);
        }
        // It must have a default config on init
        return defaultSampleConfigSettings();
    }

    private SamplingPolicySettings parseFromYml(final String ymlContent) {
        try {
            SamplingPolicySettingsReader reader = new SamplingPolicySettingsReader(new StringReader(ymlContent));
            SamplingPolicySettings settings = reader.readSettings();
            this.settingsString.set(ymlContent);
            return settings;
        } catch (Exception e) {
            log.error("[trace-sampling-policy] Failed to parse yml content: \n{}", ymlContent, e);
        }
        // Config update maybe parse fail
        return null;
    }

    private SamplingPolicySettings defaultSampleConfigSettings() {
        return new SamplingPolicySettings(null, null);
    }

}
