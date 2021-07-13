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

package org.apache.skywalking.oap.log.analyzer.dsl.spec.extractor;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.apm.util.StringUtil.isNotBlank;

public class ExtractorSpec extends AbstractSpec {

    private final List<MetricConvert> metricConverts;

    public ExtractorSpec(final ModuleManager moduleManager,
                         final LogAnalyzerModuleConfig moduleConfig) throws ModuleStartException {
        super(moduleManager, moduleConfig);

        final MeterSystem meterSystem =
            moduleManager.find(CoreModule.NAME).provider().getService(MeterSystem.class);

        metricConverts = moduleConfig.malConfigs()
                                     .stream()
                                     .map(it -> new MetricConvert(it, meterSystem))
                                     .collect(Collectors.toList());
    }

    @SuppressWarnings("unused")
    public void service(final String service) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        if (nonNull(service)) {
            BINDING.get().log().setService(service);
        }
    }

    @SuppressWarnings("unused")
    public void instance(final String instance) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        if (nonNull(instance)) {
            BINDING.get().log().setServiceInstance(instance);
        }
    }

    @SuppressWarnings("unused")
    public void endpoint(final String endpoint) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        if (nonNull(endpoint)) {
            BINDING.get().log().setEndpoint(endpoint);
        }
    }

    @SuppressWarnings("unused")
    public void tag(final Map<String, ?> kv) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        if (CollectionUtils.isEmpty(kv)) {
            return;
        }
        final LogData.Builder logData = BINDING.get().log();
        logData.setTags(
            logData.getTags()
                   .toBuilder()
                   .addAllData(
                       kv.entrySet()
                         .stream()
                         .filter(it -> isNotBlank(it.getKey()))
                         .filter(it -> nonNull(it.getValue()) &&
                             isNotBlank(Objects.toString(it.getValue())))
                         .map(it -> {
                             final Object val = it.getValue();
                             String valStr = Objects.toString(val);
                             if (Collection.class.isAssignableFrom(val.getClass())) {
                                 valStr = Joiner.on(",").skipNulls().join((Collection<?>) val);
                             }
                             return KeyStringValuePair.newBuilder()
                                                      .setKey(it.getKey())
                                                      .setValue(valStr)
                                                      .build();
                         })
                         .collect(Collectors.toList())
                   )
        );
        BINDING.get().log(logData);
    }

    @SuppressWarnings("unused")
    public void traceId(final String traceId) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        if (nonNull(traceId)) {
            final LogData.Builder logData = BINDING.get().log();
            final TraceContext.Builder traceContext = logData.getTraceContext().toBuilder();
            traceContext.setTraceId(traceId);
            logData.setTraceContext(traceContext);
        }
    }

    @SuppressWarnings("unused")
    public void segmentId(final String segmentId) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        if (nonNull(segmentId)) {
            final LogData.Builder logData = BINDING.get().log();
            final TraceContext.Builder traceContext = logData.getTraceContext().toBuilder();
            traceContext.setTraceSegmentId(segmentId);
            logData.setTraceContext(traceContext);
        }
    }

    @SuppressWarnings("unused")
    public void spanId(final String spanId) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        if (nonNull(spanId)) {
            final LogData.Builder logData = BINDING.get().log();
            final TraceContext.Builder traceContext = logData.getTraceContext().toBuilder();
            traceContext.setSpanId(Integer.parseInt(spanId));
            logData.setTraceContext(traceContext);
        }
    }

    @SuppressWarnings("unused")
    public void timestamp(final String timestamp) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        if (nonNull(timestamp) && StringUtils.isNumeric(timestamp)) {
            BINDING.get().log().setTimestamp(Long.parseLong(timestamp));
        }
    }

    @SuppressWarnings("unused")
    public void metrics(@DelegatesTo(SampleBuilder.class) final Closure<?> cl) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        final SampleBuilder builder = new SampleBuilder();
        cl.setDelegate(builder);
        cl.call();

        final Sample sample = builder.build();

        metricConverts.forEach(it -> it.toMeter(
            ImmutableMap.<String, SampleFamily>builder()
                        .put(sample.getName(), SampleFamilyBuilder.newBuilder(sample).build())
                        .build()
        ));
    }

    public static class SampleBuilder {
        @Delegate
        private final Sample.SampleBuilder sampleBuilder = Sample.builder();

        @SuppressWarnings("unused")
        public Sample.SampleBuilder labels(final Map<String, ?> labels) {
            final Map<String, String> filtered =
                labels.entrySet()
                      .stream()
                      .filter(it -> isNotBlank(it.getKey()) && nonNull(it.getValue()))
                      .collect(
                          Collectors.toMap(Map.Entry::getKey,
                                           it -> Objects.toString(it.getValue()))
                      );
            return sampleBuilder.labels(ImmutableMap.copyOf(filtered));
        }
    }
}
