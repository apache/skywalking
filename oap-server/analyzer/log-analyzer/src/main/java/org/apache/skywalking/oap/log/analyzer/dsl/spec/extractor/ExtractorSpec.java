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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.List;

import lombok.experimental.Delegate;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.extractor.slowsql.SlowSqlSpec;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.extractor.sampledtrace.SampledTraceSpec;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.DatabaseSlowStatementBuilder;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.SampledTraceBuilder;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DatabaseSlowStatement;

import org.apache.skywalking.oap.server.core.source.ISource;

import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.oap.server.library.util.StringUtil.isNotBlank;

public class ExtractorSpec extends AbstractSpec {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlowSqlSpec.class);

    private final List<MetricConvert> metricConverts;

    private final SlowSqlSpec slowSql;
    private final SampledTraceSpec sampledTrace;

    private final NamingControl namingControl;

    private final SourceReceiver sourceReceiver;

    public ExtractorSpec(final ModuleManager moduleManager,
                         final LogAnalyzerModuleConfig moduleConfig) throws ModuleStartException {
        super(moduleManager, moduleConfig);

        final MeterSystem meterSystem =
            moduleManager.find(CoreModule.NAME).provider().getService(MeterSystem.class);

        metricConverts = moduleConfig.malConfigs()
                                     .stream()
                                     .map(it -> new MetricConvert(it, meterSystem))
                                     .collect(Collectors.toList());

        slowSql = new SlowSqlSpec(moduleManager(), moduleConfig());
        sampledTrace = new SampledTraceSpec(moduleManager(), moduleConfig());

        namingControl = moduleManager.find(CoreModule.NAME)
                .provider()
                .getService(NamingControl.class);

        sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
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
    public void layer(final String layer) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        if (nonNull(layer)) {
            final LogData.Builder logData = BINDING.get().log();
            logData.setLayer(layer);
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
        final SampleFamily sampleFamily = SampleFamilyBuilder.newBuilder(sample).build();

        final Optional<List<SampleFamily>> possibleMetricsContainer = BINDING.get().metricsContainer();

        if (possibleMetricsContainer.isPresent()) {
            possibleMetricsContainer.get().add(sampleFamily);
        } else {
            metricConverts.forEach(it -> it.toMeter(
                    ImmutableMap.<String, SampleFamily>builder()
                            .put(sample.getName(), sampleFamily)
                            .build()
            ));
        }
    }

    @SuppressWarnings("unused")
    public void slowSql(@DelegatesTo(SlowSqlSpec.class) final Closure<?> cl) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        LogData.Builder log = BINDING.get().log();
        if (log.getLayer() == null
                || log.getService() == null
                || log.getTimestamp() < 1) {
            LOGGER.warn("SlowSql extracts failed, maybe something is not configured.");
            return;
        }
        DatabaseSlowStatementBuilder builder = new DatabaseSlowStatementBuilder(namingControl);
        builder.setLayer(Layer.nameOf(log.getLayer()));

        long timeBucket = TimeBucket.getTimeBucket(log.getTimestamp(), DownSampling.Minute);
        builder.setServiceName(log.getService());

        ServiceMeta serviceMeta = new ServiceMeta();
        String serviceName = namingControl.formatServiceName(log.getService());
        serviceMeta.setName(serviceName);
        serviceMeta.setLayer(builder.getLayer());
        serviceMeta.setTimeBucket(builder.getTimeBucket());
        BINDING.get().databaseSlowStatement(builder);

        cl.setDelegate(slowSql);
        cl.call();

        if (builder.getId() == null
                || builder.getLatency() < 1
                || builder.getStatement() == null) {
            LOGGER.warn("SlowSql extracts failed, maybe something is not configured.");
            return;
        }

        long timeBucketForDB = TimeBucket.getTimeBucket(log.getTimestamp(), DownSampling.Second);
        builder.setTimeBucket(timeBucketForDB);

        String entityId = serviceMeta.getEntityId();
        builder.prepare();
        DatabaseSlowStatement databaseSlowStatement = builder.toDatabaseSlowStatement();
        databaseSlowStatement.setDatabaseServiceId(entityId);

        sourceReceiver.receive(databaseSlowStatement);
        sourceReceiver.receive(serviceMeta);
    }

    @SuppressWarnings("unused")
    public void sampledTrace(@DelegatesTo(SampledTraceSpec.class) final Closure<?> cl) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        LogData.Builder log = BINDING.get().log();
        SampledTraceBuilder builder = new SampledTraceBuilder(namingControl);
        builder.setLayer(log.getLayer());
        builder.setTimestamp(log.getTimestamp());
        builder.setServiceName(log.getService());
        builder.setServiceInstanceName(log.getServiceInstance());
        builder.setTraceId(log.getTraceContext().getTraceId());
        BINDING.get().sampledTrace(builder);

        cl.setDelegate(sampledTrace);
        cl.call();

        builder.validate();
        final Record record = builder.toRecord();
        final ISource entity = builder.toEntity();
        RecordStreamProcessor.getInstance().in(record);
        sourceReceiver.receive(entity);
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
