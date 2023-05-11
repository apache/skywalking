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

package org.apache.skywalking.oap.server.analyzer.provider.trace.sampling;

import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SamplePolicySettingsReader parses the given `trace-sampling-policy-settings.yml` config file, to the target {@link
 * SamplingPolicySettings}.
 */
public class SamplingPolicySettingsReader {
    private Map<String, ?> yamlData;

    public SamplingPolicySettingsReader(InputStream inputStream) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        yamlData = yaml.load(inputStream);
    }

    public SamplingPolicySettingsReader(Reader io) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        yamlData = yaml.load(io);
    }

    /**
     * Read policy config file to {@link SamplingPolicySettings}
     */
    public SamplingPolicySettings readSettings() {
        SamplingPolicySettings samplingPolicySettings = new SamplingPolicySettings();
        if (Objects.nonNull(yamlData)) {
            readDefaultSamplingPolicy(samplingPolicySettings);
            readServicesSamplingPolicy(samplingPolicySettings);
        }
        return samplingPolicySettings;
    }

    private void readDefaultSamplingPolicy(SamplingPolicySettings samplingPolicySettings) {
        Map<String, Object> objectMap = (Map<String, Object>) yamlData.get("default");
        if (objectMap == null) {
            return;
        }
        if (objectMap.get("rate") != null) {
            samplingPolicySettings.getDefaultPolicy().setRate((Integer) objectMap.get("rate"));
        }
        if (objectMap.get("duration") != null) {
            samplingPolicySettings.getDefaultPolicy().setDuration((Integer) objectMap.get("duration"));
        }
    }

    private void readServicesSamplingPolicy(SamplingPolicySettings samplingPolicySettings) {
        Map<String, Object> objectMap = (Map<String, Object>) yamlData;
        Object servicesObject = objectMap.get("services");
        if (servicesObject != null) {
            List<Map<String, Object>> serviceList = (List<Map<String, Object>>) servicesObject;
            serviceList.forEach(service -> {
                String name = (String) service.get("name");
                if (StringUtil.isBlank(name)) {
                    return;
                }
                SamplingPolicy samplingPolicy = new SamplingPolicy();
                samplingPolicy.setRate(service.get("rate") == null ? null : (Integer) service.get("rate"));
                samplingPolicy.setDuration(service.get("duration") == null ? null : (Integer) service.get("duration"));
                samplingPolicySettings.add(name, samplingPolicy);
            });
        }
    }
}
