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
import com.google.common.collect.Sets;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.fetcher.prometheus.module.PrometheusFetcherModule;
import org.apache.skywalking.oap.server.fetcher.prometheus.provider.rule.Rule;
import org.apache.skywalking.oap.server.fetcher.prometheus.provider.rule.Rules;
import org.apache.skywalking.oap.server.fetcher.prometheus.provider.rule.StaticConfig;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.util.prometheus.Parser;
import org.apache.skywalking.oap.server.library.util.prometheus.Parsers;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Counter;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Gauge;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Metric;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.MetricFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
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
            r.getMetricsRules().forEach(rule -> {
                service.create(formatMetricName(r.getName(), rule.getName()), rule.getDownSampling(), rule.getScope());
            });
            ses.scheduleAtFixedRate(new Runnable() {
                private final Set<String> onlineRules = Sets.newHashSet();

                private final Map<GroupingRule, Queue<Pair<Long, Double>>> windows = Maps.newHashMap();

                @Override public void run() {
                    if (Objects.isNull(r.getStaticConfig())) {
                        return;
                    }
                    StaticConfig sc = r.getStaticConfig();
                    sc.getTargets().stream()
                        .flatMap(url -> {
                            Request request = new Request.Builder()
                                .url(String.format("http://%s%s", url, r.getMetricsPath().startsWith("/") ? r.getMetricsPath() : "/" + r.getMetricsPath()))
                                .build();
                            List<Metric> result = new LinkedList<>();
                            try (Response response = client.newCall(request).execute()) {
                                Parser p = Parsers.text(Objects.requireNonNull(response.body()).byteStream());
                                MetricFamily mf;
                                while ((mf = p.parse()) != null) {
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
                            } catch (IOException e) {
                                LOG.debug("Fetching {} failed", url, e);
                            }
                            return result.stream();
                        })
                        .peek(metric -> LOG.debug("Load metric {} from target", metric))
                        .flatMap(metric ->
                            r.getMetricsRules().stream()
                                .flatMap(rule -> rule.getSources().entrySet().stream().map(source -> Pair.of(rule, source)))
                                .filter(rule -> rule.getRight().getKey().equals(metric.getName()))
                                .map(rule -> Triple.of(rule.getLeft(), rule.getRight(), metric))
                        )
                        .peek(triple -> LOG.debug("Mapped rules to metrics: {}", triple))
                        // left: metricRule middle: relabelConfig right:promMetric
                        .map(triple -> {
                            String serviceName = composeEntity(triple.getMiddle().getValue().getRelabel().getService().stream(), triple.getRight().getLabels());
                            if (Objects.isNull(serviceName)) {
                                return null;
                            }
                            GroupingRule.GroupingRuleBuilder grb = GroupingRule.builder();
                            grb.name(formatMetricName(r.getName(), triple.getLeft().getName())).downSampling(triple.getLeft().getDownSampling())
                                .counterFunction(triple.getMiddle().getValue().getCounterFunction())
                                .range(triple.getMiddle().getValue().getRange());
                            switch (triple.getLeft().getScope()) {
                                case SERVICE:
                                    return Pair.of(grb.entity(MeterEntity.newService(serviceName)).build(), triple.getRight());
                                case SERVICE_INSTANCE:
                                    String instanceName = composeEntity(triple.getMiddle().getValue().getRelabel().getInstance().stream(), triple.getRight().getLabels());
                                    if (Objects.isNull(instanceName)) {
                                        return null;
                                    }
                                    return Pair.of(grb.entity(MeterEntity.newServiceInstance(serviceName, instanceName)).build(), triple.getRight());
                                default:
                                    return null;
                            }
                        })
                        .peek(triple -> LOG.debug("Generated entity from labels: {}", triple))
                        .filter(Objects::nonNull)
                        .collect(groupingBy(Pair::getLeft, mapping(Pair::getRight, toList())))
                        .forEach((rule, metrics) -> {
                            LOG.debug("Grouping by {} is {}", rule, metrics);
                            long now = System.currentTimeMillis();
                            switch (rule.getDownSampling()) {
                                case "doubleAvg":
                                    AcceptableValue<Double> value = service.buildMetrics(rule.getName(), Double.class);
                                    Double s = sumDouble(metrics);
                                    if (rule.getCounterFunction() != null) {
                                        switch (rule.getCounterFunction()) {
                                            case RATE:
                                                long windowSize = Duration.parse(rule.getRange()).toMillis();
                                                if (!windows.containsKey(rule)) {
                                                    windows.put(rule, new LinkedList<>());
                                                }
                                                Queue<Pair<Long, Double>> window = windows.get(rule);
                                                window.offer(Pair.of(now, s));
                                                Pair<Long, Double> ps = window.element();
                                                if ((now - ps.getLeft()) >= windowSize) {
                                                    window.remove();
                                                }
                                                s = (s - ps.getRight()) / ((now - ps.getLeft()) / 1000);
                                                break;
                                        }
                                    }
                                    value.accept(rule.getEntity(), s);
                                    value.setTimeBucket(TimeBucket.getMinuteTimeBucket(now));
                                    service.doStreamingCalculation(value);
                                    break;
                                default:
                                    LOG.error("Unsupported downSampling {}", rule.getDownSampling());
                            }
                        });
                }
            }, 0L, Duration.parse(r.getFetcherInterval()).getSeconds(), TimeUnit.SECONDS);
        });
    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }

    private String formatMetricName(String ruleName, String meterRuleName) {
        StringJoiner metricName = new StringJoiner("_");
        metricName.add("meter").add(ruleName).add(meterRuleName);
        return metricName.toString();
    }

    private String composeEntity(Stream<String> stream, Map<String, String> labels) {
        try {
            return stream.map(labels::get).peek(Objects::requireNonNull).collect(Collectors.joining("-"));
        } catch (NullPointerException e) {
            return null;
        }
    }

    private Double sumDouble(List<Metric> metrics) {
        return metrics.stream().map(metric -> {
            if (metric instanceof Gauge) {
                return ((Gauge) metric).getValue();
            } else if (metric instanceof Counter) {
                return ((Counter) metric).getValue();
            }
            return 0D;
        }).reduce(0D, Double::sum);
    }

}
