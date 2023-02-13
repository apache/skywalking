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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.collect.ImmutableMap.of;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public class ScopeTest {
    public static Collection<Object[]> data() {
        // This method is called before `@BeforeAll`.
        MeterEntity.setNamingControl(
            new NamingControl(512, 512, 512, new EndpointNameGrouping()));

        return Arrays.asList(new Object[][] {
            {
                "sum_service",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "svc", "catalog"))
                          .value(51)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "svc", "product"))
                          .value(50)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1"))
                          .value(100)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1"))
                          .value(3)
                          .name("http_success_request")
                          .build()
                ).build()),
                "http_success_request.sum(['idc']).service(['idc'], Layer.GENERAL)",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newService("t1", Layer.GENERAL),
                            new Sample[] {Sample.builder().labels(of()).value(200).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newService("t3", Layer.GENERAL),
                            new Sample[] {Sample.builder().labels(of()).value(54).name("http_success_request").build()}
                        );
                    }
                }
            },
            {
                "sum_service_labels",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "svc", "catalog"))
                          .value(51)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "svc", "product"))
                          .value(50)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1"))
                          .value(100)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1"))
                          .value(3)
                          .name("http_success_request")
                          .build()
                ).build()),
                "http_success_request.sum(['region', 'idc']).service(['idc'], Layer.GENERAL)",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newService("t1", Layer.GENERAL),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of("region", ""))
                                      .value(50)
                                      .name("http_success_request").build(),
                                Sample.builder()
                                      .labels(of("region", "us"))
                                      .value(150)
                                      .name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newService("t3", Layer.GENERAL),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of("region", "cn"))
                                      .value(54)
                                      .name("http_success_request").build()
                            }
                        );
                    }
                }
            },
            {
                "sum_service_m",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "svc", "catalog"))
                          .value(51)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "svc", "product"))
                          .value(50)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1"))
                          .value(100)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1"))
                          .value(3)
                          .name("http_success_request")
                          .build()
                ).build()),
                "http_success_request.sum(['idc', 'region']).service(['idc' , 'region'], Layer.GENERAL)",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newService("t1.us", Layer.GENERAL),
                            new Sample[] {Sample.builder().labels(of()).value(150).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newService("t3.cn", Layer.GENERAL),
                            new Sample[] {Sample.builder().labels(of()).value(54).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newService("t1", Layer.GENERAL),
                            new Sample[] {Sample.builder().labels(of()).value(50).name("http_success_request").build()}
                        );
                    }
                }
            },
            {
                "sum_service_endpoint",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "svc", "catalog"))
                          .value(51)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "svc", "product"))
                          .value(50)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1"))
                          .value(100)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1"))
                          .value(3)
                          .name("http_success_request")
                          .build()
                ).build()),
                "http_success_request.sum(['region', 'idc']).endpoint(['idc'] , ['region'], Layer.GENERAL)",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newEndpoint("t1", "us", Layer.GENERAL),
                            new Sample[] {Sample.builder().labels(of()).value(150).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newEndpoint("t3", "cn", Layer.GENERAL),
                            new Sample[] {Sample.builder().labels(of()).value(54).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newEndpoint("t1", "", Layer.GENERAL),
                            new Sample[] {Sample.builder().labels(of()).value(50).name("http_success_request").build()}
                        );
                    }
                }
            },

            {
                "sum_service_endpoint_labels",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "svc", "catalog"))
                          .value(51)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "svc", "product"))
                          .value(50)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1"))
                          .value(100)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1"))
                          .value(3)
                          .name("http_success_request")
                          .build()
                ).build()),
                "http_success_request.sum(['region', 'idc' , 'instance']).endpoint(['idc'] , ['region'], Layer.GENERAL)",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newEndpoint("t1", "us", Layer.GENERAL),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of("instance", ""))
                                      .value(50)
                                      .name("http_success_request").build(),
                                Sample.builder()
                                      .labels(of("instance", "10.0.0.1"))
                                      .value(100)
                                      .name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newEndpoint("t3", "cn", Layer.GENERAL),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of("instance", ""))
                                      .value(51)
                                      .name("http_success_request").build(),
                                Sample.builder()
                                      .labels(of("instance", "10.0.0.1"))
                                      .value(3)
                                      .name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newEndpoint("t1", "", Layer.GENERAL),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of("instance", ""))
                                      .value(50)
                                      .name("http_success_request").build()
                            }
                        );
                    }
                }
            },
            {
                "sum_service_endpoint_labels_m",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "svc", "product"))
                          .value(51)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "svc", "catalog"))
                          .value(50)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "svc", "catalog", "instance", "10.0.0.1"))
                          .value(100)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "svc", "product", "instance", "10.0.0.1"))
                          .value(3)
                          .name("http_success_request")
                          .build()
                ).build()),
                "http_success_request.sum(['region', 'idc' , 'svc' , 'instance']).endpoint(['idc'] , ['region','svc'], Layer.GENERAL)",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newEndpoint("t1", "us.catalog", Layer.GENERAL),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of("instance", ""))
                                      .value(50)
                                      .name("http_success_request").build(),
                                Sample.builder()
                                      .labels(of("instance", "10.0.0.1"))
                                      .value(100)
                                      .name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newEndpoint("t3", "cn.product", Layer.GENERAL),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of("instance", ""))
                                      .value(51)
                                      .name("http_success_request").build(),
                                Sample.builder()
                                      .labels(of("instance", "10.0.0.1"))
                                      .value(3)
                                      .name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newEndpoint("t1", "", Layer.GENERAL),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of("instance", ""))
                                      .value(50)
                                      .name("http_success_request").build()
                            }
                        );
                    }
                }
            },
            {
                "sum_service_instance",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "svc", "catalog"))
                          .value(51)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "svc", "product"))
                          .value(50)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1"))
                          .value(100)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1"))
                          .value(3)
                          .name("http_success_request")
                          .build()
                ).build()),
                "http_success_request.sum(['region', 'idc']).instance(['idc'] , ['region'], Layer.GENERAL)",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newServiceInstance("t1", "us", Layer.GENERAL, null),
                            new Sample[] {Sample.builder().labels(of()).value(150).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newServiceInstance("t3", "cn", Layer.GENERAL, null),
                            new Sample[] {Sample.builder().labels(of()).value(54).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newServiceInstance("t1", "", Layer.GENERAL, null),
                            new Sample[] {Sample.builder().labels(of()).value(50).name("http_success_request").build()}
                        );
                    }
                }
            },
            {
                "sum_service_instance_labels",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "svc", "catalog"))
                          .value(51)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "svc", "product"))
                          .value(50)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1"))
                          .value(100)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1"))
                          .value(3)
                          .name("http_success_request")
                          .build()
                ).build()),
                "http_success_request.sum(['region', 'idc' , 'instance']).instance(['idc'] , ['region'], Layer.GENERAL)",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newServiceInstance("t1", "us", Layer.GENERAL, null),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of("instance", ""))
                                      .value(50)
                                      .name("http_success_request").build(),
                                Sample.builder()
                                      .labels(of("instance", "10.0.0.1"))
                                      .value(100)
                                      .name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newServiceInstance("t3", "cn", Layer.GENERAL, null),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of("instance", ""))
                                      .value(51)
                                      .name("http_success_request").build(),
                                Sample.builder()
                                      .labels(of("instance", "10.0.0.1"))
                                      .value(3)
                                      .name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newServiceInstance("t1", "", Layer.GENERAL, null),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of("instance", ""))
                                      .value(50)
                                      .name("http_success_request").build()
                            }
                        );
                    }
                }
            },
            {
                "sum_service_relation",
                of("envoy_cluster_metrics_up_cx_active", SampleFamilyBuilder.newBuilder(
                    Sample.builder()
                          .labels(of("app", "productpage", "cluster_name", "details"))
                          .value(11)
                          .name("envoy_cluster_metrics_up_cx_active")
                          .build(),
                    Sample.builder()
                          .labels(of("app", "productpage", "cluster_name", "reviews"))
                          .value(16)
                          .name("envoy_cluster_metrics_up_cx_active")
                          .build()
                ).build()),
                "envoy_cluster_metrics_up_cx_active.sum(['app' ,'cluster_name']).serviceRelation(DetectPoint.CLIENT, ['app'], ['cluster_name'], Layer.GENERAL)",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newServiceRelation("productpage", "details", DetectPoint.CLIENT, Layer.GENERAL),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of())
                                      .value(11)
                                      .name("envoy_cluster_metrics_up_cx_active").build()
                            }
                        );
                        put(
                            MeterEntity.newServiceRelation("productpage", "reviews", DetectPoint.CLIENT, Layer.GENERAL),
                            new Sample[] {
                                Sample.builder()
                                      .labels(of())
                                      .value(16)
                                      .name("envoy_cluster_metrics_up_cx_active").build()
                            }
                        );
                    }
                }
            },
            {
                "sum_process_relation",
                of("rover_network_profiling_process_write_bytes", SampleFamilyBuilder.newBuilder(
                    Sample.builder()
                        .labels(of("service", "test", "instance", "test-instance", "side", "server", "client_process_id", "abc", "server_process_id", "def", "component", "1"))
                        .value(11)
                        .name("rover_network_profiling_process_write_bytes")
                        .build(),
                    Sample.builder()
                        .labels(of("service", "test", "instance", "test-instance", "side", "client", "client_process_id", "abc", "server_process_id", "def", "component", "2"))
                        .value(12)
                        .name("rover_network_profiling_process_write_bytes")
                        .build()
                ).build()),
                "rover_network_profiling_process_write_bytes.sum(['service' ,'instance', 'side', 'client_process_id', 'server_process_id', 'component'])" +
                    ".processRelation('side', ['service'], ['instance'], 'client_process_id', 'server_process_id', 'component')",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newProcessRelation("test", "test-instance", "abc", "def", 1, DetectPoint.SERVER),
                            new Sample[] {
                                Sample.builder()
                                    .labels(of())
                                    .value(11)
                                    .name("rover_network_profiling_process_write_bytes").build()
                            }
                        );
                        put(
                            MeterEntity.newProcessRelation("test", "test-instance", "abc", "def", 2, DetectPoint.CLIENT),
                            new Sample[] {
                                Sample.builder()
                                    .labels(of())
                                    .value(12)
                                    .name("rover_network_profiling_process_write_bytes").build()
                            }
                        );
                    }
                }
            }
        });
    }

    @BeforeAll
    public static void setup() {
        MeterEntity.setNamingControl(
            new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @AfterAll
    public static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void test(final String name,
                     final ImmutableMap<String, SampleFamily> input,
                     final String expression,
                     final boolean isThrow,
                     final Map<MeterEntity, Sample[]> want) {
        Expression e = DSL.parse(expression);
        Result r = null;
        try {
            r = e.run(input);
        } catch (Throwable t) {
            if (isThrow) {
                return;
            }
            log.error("Test failed", t);
            fail("Should not throw anything");
        }
        if (isThrow) {
            fail("Should throw something");
        }
        assertThat(r.isSuccess()).isEqualTo(true);
        Map<MeterEntity, Sample[]> meterSamplesR = r.getData().context.getMeterSamples();
        meterSamplesR.forEach((meterEntity, samples) -> {
            assertThat(samples).isEqualTo(want.get(meterEntity));
        });
    }
}
