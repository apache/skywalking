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

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues;
import org.apache.skywalking.oap.server.core.analysis.meter.function.PercentileFunction;
import org.apache.skywalking.oap.server.fetcher.prometheus.module.PrometheusFetcherModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

public class PrometheusFetcherProvider extends ModuleProvider {
    private final PrometheusFetcherConfig config;

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
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        if (config.isActive()) {
            // TODO. This is only a demo about fetching the data and push into the calculation stream.
            final MeterSystem service = getManager().find(CoreModule.NAME).provider().getService(MeterSystem.class);

            service.create("test_long_metrics", "avg", ScopeType.SERVICE, Long.class);
            service.create("test_histogram_metrics", "histogram", ScopeType.SERVICE, BucketedValues.class);
            service.create(
                "test_percentile_metrics", "percentile", ScopeType.SERVICE,
                PercentileFunction.PercentileArgument.class
            );

            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    final MeterEntity servEntity = MeterEntity.newService("mock_service");

                    // Long Avg Example
                    final AcceptableValue<Long> value = service.buildMetrics("test_long_metrics", Long.class);
                    value.accept(servEntity, 5L);
                    value.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
                    service.doStreamingCalculation(value);

                    // Histogram Example
                    final AcceptableValue<BucketedValues> histogramMetrics = service.buildMetrics(
                        "test_histogram_metrics", BucketedValues.class);
                    value.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
                    histogramMetrics.accept(servEntity, new BucketedValues(
                        new int[] {
                            Integer.MIN_VALUE,
                            0,
                            50,
                            100,
                            250
                        },
                        new long[] {
                            3,
                            1,
                            4,
                            10,
                            10
                        }
                    ));
                    service.doStreamingCalculation(histogramMetrics);

                    // Percentile Example
                    final AcceptableValue<PercentileFunction.PercentileArgument> testPercentileMetrics = service.buildMetrics(
                        "test_percentile_metrics", PercentileFunction.PercentileArgument.class);
                    testPercentileMetrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
                    testPercentileMetrics.accept(
                        MeterEntity.newService("service-test"),
                        new PercentileFunction.PercentileArgument(
                            new BucketedValues(
                                // Buckets
                                new int[] {
                                    0,
                                    51,
                                    100,
                                    250
                                },
                                // Values
                                new long[] {
                                    10,
                                    20,
                                    30,
                                    40
                                }
                            ),
                            // Ranks
                            new int[] {
                                50,
                                90
                            }
                        )
                    );
                    service.doStreamingCalculation(testPercentileMetrics);
                }
            }, 2, 2, TimeUnit.SECONDS);
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
