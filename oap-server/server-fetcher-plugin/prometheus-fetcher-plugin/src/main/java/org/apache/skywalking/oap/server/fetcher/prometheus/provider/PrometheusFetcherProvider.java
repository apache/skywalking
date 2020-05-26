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
import io.vavr.Function1;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.control.Try;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.Validate;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.fetcher.prometheus.module.PrometheusFetcherModule;
import org.apache.skywalking.oap.server.fetcher.prometheus.provider.downsampling.Operation;
import org.apache.skywalking.oap.server.fetcher.prometheus.provider.downsampling.Source;
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

import static java.util.Objects.requireNonNull;
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

                private final Map<Source, Queue<Tuple2<Long, Double>>> windows = Maps.newHashMap();

                private Tuple2<Long, Double> increase(Double value, Source source, long windowSize) {
                    if (!windows.containsKey(source)) {
                        windows.put(source, new LinkedList<>());
                    }
                    Queue<Tuple2<Long, Double>> window = windows.get(source);
                    long now = System.currentTimeMillis();
                    window.offer(Tuple.of(System.currentTimeMillis(), value));
                    Tuple2<Long, Double> ps = window.element();
                    if ((now - ps._1) >= windowSize) {
                        window.remove();
                    }
                    return ps;
                }

                @Override public void run() {
                    if (Objects.isNull(r.getStaticConfig())) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    StaticConfig sc = r.getStaticConfig();
                    sc.getTargets().stream()
                        .map(CheckedFunction1.liftTry(url -> {
                            Request request = new Request.Builder()
                                .url(String.format("http://%s%s", url, r.getMetricsPath().startsWith("/") ? r.getMetricsPath() : "/" + r.getMetricsPath()))
                                .build();
                            List<Metric> result = new LinkedList<>();
                            try (Response response = client.newCall(request).execute()) {
                                Parser p = Parsers.text(requireNonNull(response.body()).byteStream());
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
                            }
                            return result;
                        }))
                        .flatMap(tryIt -> PrometheusFetcherProvider.log(tryIt, "Load metric"))
                        .flatMap(Collection::stream)
                        .flatMap(metric ->
                            r.getMetricsRules().stream()
                                .flatMap(rule -> rule.getSources().entrySet().stream().map(source -> Tuple.of(rule, source.getKey(), source.getValue())))
                                .filter(rule -> rule._2.equals(metric.getName()))
                                .map(rule -> Tuple.of(rule._1, rule._2, rule._3, metric))
                        )
                        .peek(tuple -> LOG.debug("Mapped rules to metrics: {}", tuple))
                        .map(Function1.liftTry(tuple -> {
                            String serviceName = composeEntity(tuple._3.getRelabel().getService().stream(), tuple._4.getLabels());
                            Operation o = new Operation(tuple._1.getDownSampling(), formatMetricName(r.getName(), tuple._1.getName()));
                            Source.SourceBuilder sb = Source.builder();
                            sb.promMetricName(tuple._2)
                                .counterFunction(tuple._3.getCounterFunction())
                                .range(tuple._3.getRange());
                            switch (tuple._1.getScope()) {
                                case SERVICE:
                                    return Tuple.of(o, sb.entity(MeterEntity.newService(serviceName)).build(), tuple._4);
                                case SERVICE_INSTANCE:
                                    String instanceName = composeEntity(tuple._3.getRelabel().getInstance().stream(), tuple._4.getLabels());
                                    return Tuple.of(o, sb.entity(MeterEntity.newServiceInstance(serviceName, instanceName)).build(), tuple._4);
                                default:
                                    throw new IllegalArgumentException("Unsupported scope" + tuple._1.getScope());
                            }
                        }))
                        .flatMap(tryIt -> PrometheusFetcherProvider.log(tryIt, "Generated entity from labels"))
                        .collect(groupingBy(Tuple3::_1, groupingBy(Tuple3::_2, mapping(Tuple3::_3, toList()))))
                        .forEach((operation, sources) -> {
                            LOG.debug("Building metrics {} -> {}", operation, sources);
                            Try.run(() -> {
                                switch (operation.getName()) {
                                    case "doubleAvg":
                                        AcceptableValue<Double> value = service.buildMetrics(operation.getMetricName(), Double.class);
                                        Validate.isTrue(sources.size() == 1, "Can't get source for doubleAvg");
                                        Map.Entry<Source, List<Metric>> smm = sources.entrySet().iterator().next();
                                        Source s = smm.getKey();
                                        Double r = sumDouble(smm.getValue());
                                        if (s.getCounterFunction() != null) {
                                            switch (s.getCounterFunction()) {
                                                case INCREASE:
                                                    Tuple2<Long, Double> i = increase(r, s, Duration.parse(s.getRange()).toMillis());
                                                    r = r - i._2;
                                                    break;
                                                case RATE:
                                                    i = increase(r, s, Duration.parse(s.getRange()).toMillis());
                                                    r = (r - i._2) / ((now - i._1) / 1000);
                                                    break;
                                                case IRATE:
                                                    i = increase(r, s, 0);
                                                    r = (r - i._2) / ((now - i._1) / 1000);
                                            }
                                        }
                                        value.accept(s.getEntity(), r);
                                        value.setTimeBucket(TimeBucket.getMinuteTimeBucket(now));
                                        service.doStreamingCalculation(value);
                                        break;
                                    default:
                                       throw new IllegalArgumentException(String.format("Unsupported downSampling %s", operation.getName()));
                                }
                            }).onFailure(e -> LOG.debug("Building metric failed", e));
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
        return stream.map(key -> requireNonNull(labels.get(key), String.format("Getting %s from %s failed", key, labels)))
            .collect(Collectors.joining("."));
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

    private static <T> Stream<T> log(Try<T> t, String debugMessage) {
        return t
            .onSuccess(i -> LOG.debug(debugMessage + " :{}", i))
            .onFailure(e -> LOG.debug(debugMessage + " failed", e))
            .toJavaStream();
    }

}
