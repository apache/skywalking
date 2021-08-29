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

import lombok.Getter;
import lombok.ToString;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ToString
public class SamplingPolicySettings {

    @Getter
    // `default` is the keyword ,so named `global`
    private SamplingPolicy global;
    private Map<String, SamplingPolicy> services;

    /**
     * @param defaultRate     The sample rate precision is 1/10000. 10000 means 100% sample in default.
     * @param defaultDuration Setting this threshold about the latency would make the slow trace segments sampled if they cost more time, even the sampling mechanism activated. The default value is `-1`, which means would not sample slow traces. Unit, millisecond.
     */
    public SamplingPolicySettings(Integer defaultRate, Integer defaultDuration) {
        SamplingPolicy samplingPolicy = new SamplingPolicy(defaultRate == null ? 10000 : defaultRate, defaultDuration == null ? -1 : defaultDuration);
        this.global = samplingPolicy;
        this.services = new ConcurrentHashMap<>();
    }

    public void add(String service, SamplingPolicy samplingPolicy) {
        this.services.put(service, samplingPolicy);
    }

    public SamplingPolicy get(String service) {
        return this.services.get(service);
    }
}
