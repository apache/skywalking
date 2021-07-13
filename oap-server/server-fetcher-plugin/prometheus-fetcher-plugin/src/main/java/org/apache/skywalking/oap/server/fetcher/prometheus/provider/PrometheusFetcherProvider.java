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

package org.apache.skywalking.oap.server.fetcher.prometheus.provider;

import com.google.common.collect.Maps;
import io.vavr.CheckedFunction1;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.prometheus.PrometheusMetricConverter;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rules;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.StaticConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.fetcher.prometheus.http.HttpClient;
import org.apache.skywalking.oap.server.fetcher.prometheus.module.PrometheusFetcherModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.pool.CustomThreadFactory;
import org.apache.skywalking.oap.server.library.util.prometheus.Parser;
import org.apache.skywalking.oap.server.library.util.prometheus.Parsers;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Metric;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.MetricFamily;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class PrometheusFetcherProvider extends ModuleProvider {

    private final PrometheusFetcherConfig config;

    private List<Rule> rules;

    private ScheduledExecutorService ses;

    private HistogramMetrics histogram;

    private CounterMetrics errorCounter;

    public PrometheusFetcherProvider() {
        config = new PrometheusFetcherConfig();
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return PrometheusFetcherModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        rules = Rules.loadRules(config.getRulePath(), config.getEnabledRules());
        ses = Executors.newScheduledThreadPool(
            Math.min(rules.size(), config.getMaxConvertWorker()),
            new CustomThreadFactory("meter-converter")
        );
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        MetricsCreator metricsCreator = getManager().find(TelemetryModule.NAME)
                .provider()
                .getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric(
                "metrics_fetcher_latency", "The process latency of metrics scraping",
                MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE
        );
        errorCounter = metricsCreator.createCounter("metrics_fetcher_error_count", "The error number of metrics scraping",
                MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE
        );
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        if (rules.isEmpty()) {
            return;
        }
        final MeterSystem service = getManager().find(CoreModule.NAME).provider().getService(MeterSystem.class);
        rules.forEach(r -> {
            ses.scheduleAtFixedRate(new Runnable() {

                private final PrometheusMetricConverter converter = new PrometheusMetricConverter(r, service);

                @Override public void run() {
                    try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
                        if (Objects.isNull(r.getStaticConfig())) {
                            return;
                        }
                        StaticConfig sc = r.getStaticConfig();
                        long now = System.currentTimeMillis();
                        converter.toMeter(sc.getTargets().stream()
                                .map(CheckedFunction1.liftTry(target -> {
                                    URI url = new URI(target.getUrl());
                                    URI targetURL = url.resolve(r.getMetricsPath());
                                    String content = HttpClient.builder().url(targetURL.toString()).caFilePath(target.getSslCaFilePath()).build().request();
                                    List<Metric> result = new ArrayList<>();
                                    try (InputStream targetStream = new ByteArrayInputStream(content.getBytes(Charsets.UTF_8))) {
                                        Parser p = Parsers.text(targetStream);
                                        MetricFamily mf;
                                        while ((mf = p.parse(now)) != null) {
                                            mf.getMetrics().forEach(metric -> {
                                                if (Objects.isNull(sc.getLabels())) {
                                                    return;
                                                }
                                                Map<String, String> extraLabels = Maps.newHashMap(sc.getLabels());
                                                extraLabels.put("instance", target.getUrl());
                                                extraLabels.forEach((key, value) -> {
                                                    if (metric.getLabels().containsKey(key)) {
                                                        metric.getLabels().put("exported_" + key, metric.getLabels().get(key));
                                                    }
                                                    metric.getLabels().put(key, value);
                                                });
                                            });
                                            result.addAll(mf.getMetrics());
                                        }
                                    }
                                    if (log.isDebugEnabled()) {
                                        log.debug("Fetch metrics from prometheus: {}", result);
                                    }
                                    return result;
                                }))
                                .flatMap(tryIt -> MetricConvert.log(tryIt, "Load metric"))
                                .flatMap(Collection::stream));
                    } catch (Exception e) {
                        errorCounter.inc();
                        log.error(e.getMessage(), e);
                    }
                }
            }, 0L, Duration.parse(r.getFetcherInterval()).getSeconds(), TimeUnit.SECONDS);
        });
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            TelemetryModule.NAME,
            CoreModule.NAME
        };
    }
}
