/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.vservice;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.analyzer.genai.service.IGenAIMeterAnalyzerService;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.GenAIMetrics;
import org.apache.skywalking.oap.server.core.source.GenAIModelAccess;
import org.apache.skywalking.oap.server.core.source.GenAIProviderAccess;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@RequiredArgsConstructor
public class VirtualGenAIProcessor implements VirtualServiceProcessor {

    private final NamingControl namingControl;

    private final IGenAIMeterAnalyzerService meterAnalyzerService;

    private final List<Source> recordList = new ArrayList<>();

    @Override
    public void prepareVSIfNecessary(SpanObject span, SegmentObject segmentObject) {
        if (span.getSpanLayer() != SpanLayer.GenAI) {
            return;
        }

        GenAIMetrics metrics = meterAnalyzerService.extractMetricsFromSWSpan(span, segmentObject);
        if (metrics == null) {
            return;
        }

        recordList.add(toServiceMeta(metrics));
        recordList.add(toProviderAccess(metrics));
        recordList.add(toModelAccess(metrics));
    }

    private ServiceMeta toServiceMeta(GenAIMetrics metrics) {
        ServiceMeta service = new ServiceMeta();
        service.setName(metrics.getProviderName());
        service.setLayer(Layer.VIRTUAL_GENAI);
        service.setTimeBucket(metrics.getTimeBucket());
        return service;
    }

    private GenAIProviderAccess toProviderAccess(GenAIMetrics metrics) {
        GenAIProviderAccess source = new GenAIProviderAccess();
        source.setName(namingControl.formatServiceName(metrics.getProviderName()));
        source.setInputTokens(metrics.getInputTokens());
        source.setOutputTokens(metrics.getOutputTokens());
        source.setTotalCost(metrics.getTotalCost());
        source.setLatency(metrics.getLatency());
        source.setStatus(metrics.isStatus());
        source.setTimeBucket(metrics.getTimeBucket());
        return source;
    }

    private GenAIModelAccess toModelAccess(GenAIMetrics metrics) {
        GenAIModelAccess source = new GenAIModelAccess();
        source.setServiceName(namingControl.formatServiceName(metrics.getProviderName()));
        source.setModelName(namingControl.formatInstanceName(metrics.getModelName()));
        source.setInputTokens(metrics.getInputTokens());
        source.setOutputTokens(metrics.getOutputTokens());
        source.setTotalCost(metrics.getTotalCost());
        source.setTimeToFirstToken(metrics.getTimeToFirstToken());
        source.setLatency(metrics.getLatency());
        source.setStatus(metrics.isStatus());
        source.setTimeBucket(metrics.getTimeBucket());
        return source;
    }

    @Override
    public void emitTo(Consumer<Source> consumer) {
        for (Source source : recordList) {
            if (source != null) {
                consumer.accept(source);
            }
        }
    }
}
