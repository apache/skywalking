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
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.metric.promethues.PrometheusMetricConverter;
import org.apache.skywalking.oap.server.core.metric.promethues.rule.Rule;
import org.apache.skywalking.oap.server.core.metric.promethues.rule.Rules;
import org.apache.skywalking.oap.server.core.metric.promethues.rule.StaticConfig;
import org.apache.skywalking.oap.server.fetcher.prometheus.module.PrometheusFetcherModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.util.prometheus.Parser;
import org.apache.skywalking.oap.server.library.util.prometheus.Parsers;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Metric;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.MetricFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

public class PrometheusFetcherProvider extends ModuleProvider {
    private static final Logger LOG = LoggerFactory.getLogger(PrometheusFetcherProvider.class);

    private final PrometheusFetcherConfig config;

    private final OkHttpClient client = new OkHttpClient();

    private List<Rule> rules;

    private ScheduledExecutorService ses;

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
        if (!config.isActive()) {
            return;
        }
        rules = Rules.loadRules(config.getRulePath());
        ses = Executors.newScheduledThreadPool(rules.size(), Executors.defaultThreadFactory());
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        if (!config.isActive()) {
            return;
        }
        final MeterSystem service = getManager().find(CoreModule.NAME).provider().getService(MeterSystem.class);
        rules.forEach(r -> {
            ses.scheduleAtFixedRate(new Runnable() {

                private final PrometheusMetricConverter converter = new PrometheusMetricConverter(r.getMetricsRules(), service);

                @Override public void run() {
                    if (Objects.isNull(r.getStaticConfig())) {
                        return;
                    }
                    StaticConfig sc = r.getStaticConfig();
                    long now = System.currentTimeMillis();
                    converter.toMeter(sc.getTargets().stream()
                        .map(CheckedFunction1.liftTry(url -> {
                            Request request = new Request.Builder()
                                .url(String.format("http://%s%s", url, r.getMetricsPath().startsWith("/") ? r.getMetricsPath() : "/" + r.getMetricsPath()))
                                .build();
                            List<Metric> result = new LinkedList<>();
                            try (Response response = client.newCall(request).execute()) {
                                Parser p = Parsers.text(requireNonNull(response.body()).byteStream());
                                MetricFamily mf;

                                while ((mf = p.parse(now)) != null) {
                                    result.addAll(mf.getMetrics().stream()
                                        .peek(metric -> {
                                            Map<String, String> extraLabels = Maps.newHashMap(sc.getLabels());
                                            extraLabels.put("instance", url);
                                            extraLabels.forEach((key, value) -> {
                                                if (metric.getLabels().containsKey(key)) {
                                                    metric.getLabels().put("exported_" + key, metric.getLabels().get(key));
                                                }
                                                metric.getLabels().put(key, value);
                                            });
                                        })
                                        .collect(toList()));
                                }
                            }
                            return result;
                        }))
                        .flatMap(tryIt -> PrometheusMetricConverter.log(tryIt, "Load metric"))
                        .flatMap(Collection::stream));
                    }
            }, 0L, Duration.parse(r.getFetcherInterval()).getSeconds(), TimeUnit.SECONDS);
        });
    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }

}
