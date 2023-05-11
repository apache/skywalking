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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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
import org.apache.skywalking.oap.server.core.source.CacheAccess;
import org.apache.skywalking.oap.server.core.source.VirtualCacheOperation;
import org.apache.skywalking.oap.server.core.source.CacheSlowAccess;
import org.apache.skywalking.oap.server.library.util.StringUtil;

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
                                       .collect(
                                           Collectors.toMap(KeyStringValuePair::getKey, KeyStringValuePair::getValue));

        String cacheType = tags.get(SpanTags.CACHE_TYPE);
        if (StringUtil.isBlank(cacheType)) {
            return;
        }
        cacheType = cacheType.toLowerCase();
        String peer = span.getPeer();
        // peer is blank if it's a local span.
        if (StringUtil.isBlank(peer)) {
            peer = tags.get(SpanTags.CACHE_TYPE) + "-local";
        }
        long timeBucket = TimeBucket.getMinuteTimeBucket(span.getStartTime());
        String serviceName = namingControl.formatServiceName(peer);
        int latency = (int) (span.getEndTime() - span.getStartTime());
        sourceList.add(parseServiceMeta(serviceName, timeBucket));
        VirtualCacheOperation op = parseOperation(tags.get(SpanTags.CACHE_OP));
        if ((op == VirtualCacheOperation.Write && latency > config.getCacheWriteLatencyThresholdsAndWatcher()
                                                                  .getThreshold(cacheType))
            || (op == VirtualCacheOperation.Read && latency > config.getCacheReadLatencyThresholdsAndWatcher()
                                                                    .getThreshold(cacheType))) {
            CacheSlowAccess slowAccess = new CacheSlowAccess();
            slowAccess.setCacheServiceId(IDManager.ServiceID.buildId(serviceName, false));
            slowAccess.setLatency(latency);
            slowAccess.setId(segmentObject.getTraceSegmentId() + "-" + span.getSpanId());
            slowAccess.setStatus(!span.getIsError());
            slowAccess.setTraceId(segmentObject.getTraceId());
            slowAccess.setCommand(tags.get(SpanTags.CACHE_CMD));
            slowAccess.setKey(tags.get(SpanTags.CACHE_KEY));
            slowAccess.setTimeBucket(TimeBucket.getRecordTimeBucket(span.getStartTime()));
            slowAccess.setTimestamp(span.getStartTime());
            slowAccess.setOperation(op);
            sourceList.add(slowAccess);
        }
        CacheAccess access = new CacheAccess();
        access.setCacheTypeId(span.getComponentId());
        access.setLatency(latency);
        access.setName(serviceName);
        access.setStatus(!span.getIsError());
        access.setTimeBucket(timeBucket);
        access.setOperation(op);
        sourceList.add(access);
    }

    private ServiceMeta parseServiceMeta(String serviceName, long timeBucket) {
        ServiceMeta service = new ServiceMeta();
        service.setName(serviceName);
        service.setLayer(Layer.VIRTUAL_CACHE);
        service.setTimeBucket(timeBucket);
        return service;
    }

    private VirtualCacheOperation parseOperation(String op) {
        if ("write".equals(op)) {
            return VirtualCacheOperation.Write;
        }
        if ("read".equals(op)) {
            return VirtualCacheOperation.Read;
        }
        return VirtualCacheOperation.Others;
    }

    @Override
    public void emitTo(Consumer<Source> consumer) {
        sourceList.forEach(consumer);
    }

}
