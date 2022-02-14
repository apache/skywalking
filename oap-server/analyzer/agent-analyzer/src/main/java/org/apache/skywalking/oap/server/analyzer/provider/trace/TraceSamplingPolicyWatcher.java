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
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.sampling.SamplingPolicy;
import org.apache.skywalking.oap.server.analyzer.provider.trace.sampling.SamplingPolicySettings;
import org.apache.skywalking.oap.server.analyzer.provider.trace.sampling.SamplingPolicySettingsReader;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.isNull;

@Slf4j
public class TraceSamplingPolicyWatcher extends ConfigChangeWatcher {

    private final AtomicReference<String> settingsString = new AtomicReference<>(null);
    private final AtomicReference<SamplingPolicySettings> samplingPolicySettings = new AtomicReference<>(null);
    private final SamplingPolicySettings defaultSamplingPolicySettings;

    public TraceSamplingPolicyWatcher(AnalyzerModuleConfig moduleConfig, ModuleProvider provider) {
        super(AnalyzerModule.NAME, provider, "traceSamplingPolicy");
        this.defaultSamplingPolicySettings = parseFromFile(moduleConfig.getTraceSamplingPolicySettingsFile());
        loadDefaultPolicySettings();
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType()) || StringUtil.isBlank(value.getNewValue())) {
            this.settingsString.set(null);
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

    /**
     * Determine whether need to be sampled
     *
     * @param service  service's name
     * @param sample   sample rate of trace segment
     * @param duration duration of trace segment
     * @return
     */
    public boolean shouldSample(String service, int sample, int duration) {
        SamplingPolicy samplingPolicy = this.samplingPolicySettings.get().get(service);
        if (samplingPolicy == null) {
            return shouldSampleByDefault(sample, duration);
        }
        return shouldSampleService(samplingPolicy, sample, duration);
    }

    /**
     * When 'duration' is over 'default trace segment's slow threshold' that should be sampled. Or when 'sample' is with
     * in [0,defaultSamplingRate) that also should be sampled.
     *
     * @param sample   sample rate of trace segment
     * @param duration duration of trace segment
     * @return
     */
    private boolean shouldSampleByDefault(int sample, int duration) {
        return isOverDefaultSlowThreshold(duration) || withinDefaultRateRange(sample);
    }

    /**
     * On the basis of service's If the specific service's 'trace segment's slow threshold' is not null. The same as
     * 'samplingRate', if the specific service's 'samplingRate' is not null. Otherwise,Using the default sampling
     * policy.
     * <p>
     * The priority of sampling policy: 'trace segment's slow threshold' > 'samplingRate',no matter the service's or
     * global. When 'duration' is over 'default trace segment's slow threshold' that should be sampled. Or when 'sample'
     * is with in [0,defaultSamplingRate) that also should be sampled.
     *
     * @param samplingPolicy the sampling policy of the specific service
     * @param sample         sample rate of trace segment
     * @param duration       duration of trace segment
     * @return
     */
    private boolean shouldSampleService(SamplingPolicy samplingPolicy, int sample, int duration) {
        return (samplingPolicy.getDuration() != null && isOverSlowThreshold(duration, samplingPolicy.getDuration()))
            || (samplingPolicy.getRate() != null && withinRateRange(sample, samplingPolicy.getRate()))
            // global policy
            || (samplingPolicy.getDuration() == null && isOverDefaultSlowThreshold(duration))
            || (samplingPolicy.getRate() == null && withinDefaultRateRange(sample));
    }

    private boolean withinDefaultRateRange(int sample) {
        return withinRateRange(sample, this.samplingPolicySettings.get().getDefaultPolicy().getRate());
    }

    private boolean isOverDefaultSlowThreshold(int duration) {
        return isOverSlowThreshold(duration, this.samplingPolicySettings.get().getDefaultPolicy().getDuration());
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
            log.info(
                "[trace-sampling-policy] Parse yaml fail, retain last configuration: {}", this.samplingPolicySettings);
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
        return new SamplingPolicySettings();
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

}
