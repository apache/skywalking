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
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.analyzer.provider.trace.CustomTraceSampleRateWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.TraceSampleRateWatcher;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.util.List;

/**
 * The sampler makes the sampling mechanism works at backend side. Sample result: [0,sampleRate) sampled, (sampleRate,~)
 * ignored
 */
public class TraceSegmentSampler {

    private TraceSampleRateWatcher traceSampleRateWatcher;
    private CustomTraceSampleRateWatcher customTraceSampleRateWatcher;

    public TraceSegmentSampler(TraceSampleRateWatcher traceSampleRateWatcher,
                               CustomTraceSampleRateWatcher customTraceSampleRateWatcher) {
        this.traceSampleRateWatcher = traceSampleRateWatcher;
        this.customTraceSampleRateWatcher = customTraceSampleRateWatcher;
    }

    public boolean shouldSample(SegmentObject segmentObject, int duration) {
        int sample = Math.abs(segmentObject.getTraceId().hashCode()) % 10000;
        String serviceName = segmentObject.getService();
        String serviceInstanceName = segmentObject.getServiceInstance();
        CustomTraceSampleRateWatcher.ServiceInfo serviceInfo = customTraceSampleRateWatcher.getSample(serviceName);
        if (serviceInfo != null) {
            // first endpoint latitude
            if (endpoint(segmentObject, serviceInfo, sample, duration)) {
                return true;
            }
            // second instance latitude
            if (instance(serviceInfo, serviceInstanceName, sample, duration)) {
                return true;
            }
            // third service latitude
            if (service(serviceInfo, sample, duration)) {
                return true;
            }
        }
        return sample < traceSampleRateWatcher.getSampleRate();
    }

    private boolean endpoint(SegmentObject segmentObject, CustomTraceSampleRateWatcher.ServiceInfo serviceInfo, int sample, int duration) {
        List<SpanObject> spansList = segmentObject.getSpansList();
        if (CollectionUtils.isEmpty(spansList) || CollectionUtils.isEmpty(serviceInfo.getEndpoints())) {
            return false;
        }
        CustomTraceSampleRateWatcher.SampleInfo endpoint = null;
        for (CustomTraceSampleRateWatcher.SampleInfo sampleInfo : serviceInfo.getEndpoints()) {
            if (StringUtil.isBlank(sampleInfo.getName())) {
                continue;
            }
            if (spansList.stream().anyMatch(s -> sampleInfo.getName().equals(s.getOperationName()))) {
                endpoint = sampleInfo;
                break;
            }
        }
        if (endpoint != null) {
            if (endpoint.getDuration() != null && duration > endpoint.getDuration().get()) {
                return true;
            }
            if (endpoint.getSampleRate() != null && sample < endpoint.getSampleRate().get()) {
                return true;
            }
        }
        return false;
    }

    private boolean instance(CustomTraceSampleRateWatcher.ServiceInfo serviceInfo, String serviceInstanceName, int sample, int duration) {
        if (CollectionUtils.isEmpty(serviceInfo.getInstances()) || serviceInstanceName == null) {
            return false;
        }
        CustomTraceSampleRateWatcher.SampleInfo instance = null;
        for (CustomTraceSampleRateWatcher.SampleInfo sampleInfo : serviceInfo.getInstances()) {
            if (serviceInstanceName.equals(sampleInfo.getName())) {
                instance = sampleInfo;
                break;
            }
        }
        if (instance != null) {
            if (instance.getDuration() != null && duration > instance.getDuration().get()) {
                return true;
            }
            if (instance.getSampleRate() != null && sample < instance.getSampleRate().get()) {
                return true;
            }
        }
        return false;
    }

    private boolean service(CustomTraceSampleRateWatcher.ServiceInfo serviceInfo, int sample, int duration) {
        if (serviceInfo.getDuration() != null && duration > serviceInfo.getDuration().get()) {
            return true;
        }
        if (serviceInfo.getSampleRate() != null && sample < serviceInfo.getSampleRate().get()) {
            return true;
        }
        return false;
    }
}
