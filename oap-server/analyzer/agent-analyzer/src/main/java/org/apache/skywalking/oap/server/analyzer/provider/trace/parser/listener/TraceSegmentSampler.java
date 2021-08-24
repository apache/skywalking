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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener;

import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.analyzer.provider.trace.TraceSampleRateSettingWatcher;

/**
 * The sampler makes the sampling mechanism works at backend side. Sample result: [0,sampleRate) sampled, (sampleRate,~)
 * ignored
 */
public class TraceSegmentSampler {
    private TraceSampleRateSettingWatcher traceSampleRateSettingWatcher;

    public TraceSegmentSampler(TraceSampleRateSettingWatcher traceSampleRateSettingWatcher) {
        this.traceSampleRateSettingWatcher = traceSampleRateSettingWatcher;
    }

    public boolean shouldSample(SegmentObject segmentObject, int duration) {
        int sample = Math.abs(segmentObject.getTraceId().hashCode()) % 10000;
        String serviceName = segmentObject.getService();
        TraceSampleRateSettingWatcher.ServiceSampleConfig sampleConfig = traceSampleRateSettingWatcher.getSample(serviceName);
        if (sampleConfig != null) {
            if (service(sampleConfig, sample, duration)) {
                return true;
            }
        }
        return sample < traceSampleRateSettingWatcher.getSampleRate();
    }

    private boolean service(TraceSampleRateSettingWatcher.ServiceSampleConfig sampleConfig, int sample, int duration) {
        // trace latency
        if (sampleConfig.getDuration() != null && duration > sampleConfig.getDuration().get()) {
            return true;
        }
        // sampling rate
        if (sampleConfig.getSampleRate() != null && sample < sampleConfig.getSampleRate().get()) {
            return true;
        }
        return false;
    }
}
