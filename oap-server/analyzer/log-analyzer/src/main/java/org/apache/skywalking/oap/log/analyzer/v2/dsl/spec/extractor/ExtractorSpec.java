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

package org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.sampledtrace.SampledTraceSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.slowsql.SlowSqlSpec;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.DatabaseSlowStatementBuilder;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.SampledTraceBuilder;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
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

        LogAnalyzerModuleProvider provider = (LogAnalyzerModuleProvider) moduleManager
            .find(LogAnalyzerModule.NAME).provider();

        metricConverts = provider.getMetricConverts();

        slowSql = new SlowSqlSpec(moduleManager(), moduleConfig());
        sampledTrace = new SampledTraceSpec(moduleManager(), moduleConfig());

        namingControl = moduleManager.find(CoreModule.NAME)
                                     .provider()
                                     .getService(NamingControl.class);

        sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
    }

    public void service(final ExecutionContext ctx, final String service) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(service)) {
            ctx.log().setService(service);
        }
    }

    public void instance(final ExecutionContext ctx, final String instance) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(instance)) {
            ctx.log().setServiceInstance(instance);
        }
    }

    public void endpoint(final ExecutionContext ctx, final String endpoint) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(endpoint)) {
            ctx.log().setEndpoint(endpoint);
        }
    }

    public void tag(final ExecutionContext ctx, final Map<String, ?> kv) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (CollectionUtils.isEmpty(kv)) {
            return;
        }
        final LogData.Builder logData = ctx.log();
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
    }

    public void traceId(final ExecutionContext ctx, final String traceId) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(traceId)) {
            final LogData.Builder logData = ctx.log();
            final TraceContext.Builder traceContext = logData.getTraceContext().toBuilder();
            traceContext.setTraceId(traceId);
            logData.setTraceContext(traceContext);
        }
    }

    public void segmentId(final ExecutionContext ctx, final String segmentId) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(segmentId)) {
            final LogData.Builder logData = ctx.log();
            final TraceContext.Builder traceContext = logData.getTraceContext().toBuilder();
            traceContext.setTraceSegmentId(segmentId);
            logData.setTraceContext(traceContext);
        }
    }

    public void spanId(final ExecutionContext ctx, final String spanId) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(spanId)) {
            final LogData.Builder logData = ctx.log();
            final TraceContext.Builder traceContext = logData.getTraceContext().toBuilder();
            traceContext.setSpanId(Integer.parseInt(spanId));
            logData.setTraceContext(traceContext);
        }
    }

    public void timestamp(final ExecutionContext ctx, final String timestamp) {
        timestamp(ctx, timestamp, null);
    }

    public void timestamp(final ExecutionContext ctx, final String timestamp,
                           final String formatPattern) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (StringUtil.isEmpty(timestamp)) {
            return;
        }

        if (StringUtil.isEmpty(formatPattern)) {
            if (StringUtils.isNumeric(timestamp)) {
                ctx.log().setTimestamp(Long.parseLong(timestamp));
            }
        } else {
            SimpleDateFormat format = new SimpleDateFormat(formatPattern);
            try {
                ctx.log().setTimestamp(format.parse(timestamp).getTime());
            } catch (ParseException e) {
                // ignore
            }
        }
    }

    public void layer(final ExecutionContext ctx, final String layer) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(layer)) {
            ctx.log().setLayer(layer);
        }
    }

    public SampleBuilder prepareMetrics(final ExecutionContext ctx) {
        if (ctx.shouldAbort()) {
            return null;
        }
        return new SampleBuilder();
    }

    public void submitMetrics(final ExecutionContext ctx, final SampleBuilder builder) {
        if (ctx.shouldAbort() || builder == null) {
            return;
        }
        final Sample sample = builder.build();
        final SampleFamily sampleFamily = SampleFamilyBuilder.newBuilder(sample).build();

        final Optional<List<SampleFamily>> possibleMetricsContainer = ctx.metricsContainer();

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

    public SampledTraceSpec sampledTraceSpec() {
        return sampledTrace;
    }

    public SlowSqlSpec slowSqlSpec() {
        return slowSql;
    }

    public void prepareSampledTrace(final ExecutionContext ctx) {
        if (ctx.shouldAbort()) {
            return;
        }
        final LogData.Builder log = ctx.log();
        final SampledTraceBuilder builder = new SampledTraceBuilder(namingControl);
        builder.setLayer(log.getLayer());
        builder.setTimestamp(log.getTimestamp());
        builder.setServiceName(log.getService());
        builder.setServiceInstanceName(log.getServiceInstance());
        builder.setTraceId(log.getTraceContext().getTraceId());
        ctx.sampledTrace(builder);
    }

    public void submitSampledTrace(final ExecutionContext ctx) {
        if (ctx.shouldAbort()) {
            return;
        }
        final SampledTraceBuilder builder = ctx.sampledTraceBuilder();
        if (builder == null) {
            return;
        }
        builder.validate();
        final Record record = builder.toRecord();
        final ISource entity = builder.toEntity();
        RecordStreamProcessor.getInstance().in(record);
        sourceReceiver.receive(entity);
    }

    public void prepareSlowSql(final ExecutionContext ctx) {
        if (ctx.shouldAbort()) {
            return;
        }
        final LogData.Builder log = ctx.log();
        if (log.getLayer() == null
            || log.getService() == null
            || log.getTimestamp() < 1) {
            LOGGER.warn("SlowSql extracts failed, maybe something is not configured.");
            return;
        }
        final DatabaseSlowStatementBuilder builder = new DatabaseSlowStatementBuilder(namingControl);
        builder.setLayer(Layer.nameOf(log.getLayer()));
        builder.setServiceName(log.getService());
        ctx.databaseSlowStatement(builder);
    }

    public void submitSlowSql(final ExecutionContext ctx) {
        if (ctx.shouldAbort()) {
            return;
        }
        final DatabaseSlowStatementBuilder builder = ctx.databaseSlowStatement();
        if (builder == null) {
            return;
        }
        if (builder.getId() == null
            || builder.getLatency() < 1
            || builder.getStatement() == null) {
            LOGGER.warn("SlowSql extracts failed, maybe something is not configured.");
            return;
        }
        final LogData.Builder log = ctx.log();
        final long timeBucketForDB = TimeBucket.getTimeBucket(log.getTimestamp(), DownSampling.Second);
        builder.setTimeBucket(timeBucketForDB);
        builder.setTimestamp(log.getTimestamp());
        builder.prepare();
        sourceReceiver.receive(builder.toDatabaseSlowStatement());

        final ServiceMeta serviceMeta = new ServiceMeta();
        serviceMeta.setName(builder.getServiceName());
        serviceMeta.setLayer(builder.getLayer());
        final long timeBucket = TimeBucket.getTimeBucket(log.getTimestamp(), DownSampling.Minute);
        serviceMeta.setTimeBucket(timeBucket);
        sourceReceiver.receive(serviceMeta);
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
                          Collectors.toMap(
                              Map.Entry::getKey,
                              it -> Objects.toString(it.getValue())
                          )
                      );
            return sampleBuilder.labels(ImmutableMap.copyOf(filtered));
        }
    }
}
