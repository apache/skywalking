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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.vservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.Source;
import org.apache.skywalking.oap.server.core.source.VirtualCacheAccess;
import org.apache.skywalking.oap.server.core.source.VirtualCacheSlowAccess;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class VirtualCacheProcessor implements VirtualServiceProcessor {

    private final NamingControl namingControl;

    private final AnalyzerModuleConfig config;

    private final List<Source> sourceList = new ArrayList<>();

    @Override
    public void prepareVSIfNecessary(SpanObject span, SegmentObject segmentObject) {
        if (span.getSpanLayer() != SpanLayer.Cache) {
            return;
        }
        Map<String, String> tags = span.getTagsList().stream()
                .collect(Collectors.toMap(KeyStringValuePair::getKey, KeyStringValuePair::getValue));
        if (!(tags.containsKey(SpanTags.CACHE_KEY) && tags.containsKey(SpanTags.CACHE_OP) && tags.containsKey(SpanTags.CACHE_CMD) && tags.containsKey(SpanTags.CACHE_TYPE))) {
            return;
        }
        String cacheType = tags.get(SpanTags.CACHE_TYPE).toLowerCase();
        String peer = span.getPeer();
        // peer is blank if it's a local span.
        if (StringUtil.isBlank(peer)) {
            peer = tags.get(SpanTags.CACHE_TYPE) + "-local";
        }
        long timeBucket = TimeBucket.getMinuteTimeBucket(span.getStartTime());
        String serviceName = namingControl.formatServiceName(peer);
        int latency = (int) (span.getEndTime() - span.getStartTime());
        sourceList.add(parseServiceMeta(serviceName, timeBucket));
        String op = tags.get(SpanTags.CACHE_OP);
        if (("write".equals(op) && latency > config.getCacheWriteLatencyThresholdsAndWatcher().getThreshold(cacheType))
                || ("read".equals(op) && latency > config.getCacheReadLatencyThresholdsAndWatcher().getThreshold(cacheType))) {
            VirtualCacheSlowAccess slowAccess = new VirtualCacheSlowAccess();
            slowAccess.setCacheServiceId(IDManager.ServiceID.buildId(serviceName, false));
            slowAccess.setLatency(latency);
            slowAccess.setId(segmentObject.getTraceSegmentId() + "-" + span.getSpanId());
            slowAccess.setStatus(!span.getIsError());
            slowAccess.setTraceId(segmentObject.getTraceId());
            slowAccess.setCommand(tags.get(SpanTags.CACHE_CMD));
            slowAccess.setKey(tags.get(SpanTags.CACHE_KEY));
            slowAccess.setTimeBucket(TimeBucket.getRecordTimeBucket(span.getStartTime()));
            slowAccess.setOp(op);
            sourceList.add(slowAccess);
        }
        VirtualCacheAccess access = new VirtualCacheAccess();
        access.setCacheTypeId(span.getComponentId());
        access.setLatency(latency);
        access.setName(serviceName);
        access.setStatus(!span.getIsError());
        access.setTimeBucket(timeBucket);
        access.setOp(op);
        sourceList.add(access);
    }

    private ServiceMeta parseServiceMeta(String serviceName, long timeBucket) {
        ServiceMeta service = new ServiceMeta();
        service.setName(serviceName);
        service.setLayer(Layer.VIRTUAL_CACHE);
        service.setTimeBucket(timeBucket);
        return service;
    }

    @Override
    public void emitTo(Consumer<Source> consumer) {
        sourceList.forEach(consumer);
    }

}
