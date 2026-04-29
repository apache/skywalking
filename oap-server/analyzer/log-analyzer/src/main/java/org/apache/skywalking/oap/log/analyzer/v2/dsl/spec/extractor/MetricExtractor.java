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

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.Delegate;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import static java.util.Objects.nonNull;
import static org.apache.skywalking.oap.server.library.util.StringUtil.isNotBlank;

/**
 * Handles LAL {@code metrics {}} blocks — prepares and submits metric samples
 * to the MAL pipeline.
 *
 * <p>Standard field setters (service, layer, timestamp, etc.) and tags are no
 * longer handled here — they route directly to the output builder via
 * compile-time setter resolution in the generated code.
 */
public class MetricExtractor extends AbstractSpec {
    /**
     * Resolved once at construction — cheaper than re-looking-up on every sample. The provider's
     * converter registry is mutated under runtime-rule hot-update, but the provider reference
     * itself never changes for the lifetime of this JVM, so caching it is safe.
     */
    private final LogAnalyzerModuleProvider provider;

    public MetricExtractor(final ModuleManager moduleManager,
                           final LogAnalyzerModuleConfig moduleConfig) throws ModuleStartException {
        super(moduleManager, moduleConfig);

        this.provider = (LogAnalyzerModuleProvider) moduleManager
            .find(LogAnalyzerModule.NAME).provider();
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
            // Re-read the converter snapshot on every submit. Hot-updates publish a new map
            // reference through LogAnalyzerModuleProvider, so reading at this point picks up
            // freshly-applied runtime rules without an extra signal from the reconciler.
            provider.getMetricConverts().forEach(it -> it.toMeter(
                ImmutableMap.<String, SampleFamily>builder()
                            .put(sample.getName(), sampleFamily)
                            .build()
            ));
        }
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
