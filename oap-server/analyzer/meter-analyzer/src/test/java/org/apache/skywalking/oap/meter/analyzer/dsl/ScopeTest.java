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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static com.google.common.collect.ImmutableMap.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

@Slf4j
@RunWith(Parameterized.class)
public class ScopeTest {

    @Parameterized.Parameter
    public String name;

    @Parameterized.Parameter(1)
    public ImmutableMap<String, SampleFamily> input;

    @Parameterized.Parameter(2)
    public String expression;

    @Parameterized.Parameter(3)
    public boolean isThrow;

    @Parameterized.Parameter(4)
    public Map<MeterEntity, Sample[]> want;

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "sum_service",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(51).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).value(3).name("http_success_request").build()
                ).build()),
                "http_success_request.sum(['idc']).service(['idc'])",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newService("t1"),
                            new Sample[] {Sample.builder().labels(of()).value(200).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newService("t3"),
                            new Sample[] {Sample.builder().labels(of()).value(54).name("http_success_request").build()}
                        );
                    }
                }
            },
            {
                "sum_service_labels",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(51).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).value(3).name("http_success_request").build()
                ).build()),
                "http_success_request.sum(['region', 'idc']).service(['idc'])",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newService("t1"),
                            new Sample[] {
                                Sample.builder().labels(of("region", "")).value(50).name("http_success_request").build(),
                                Sample.builder().labels(of("region", "us")).value(150).name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newService("t3"),
                            new Sample[] {Sample.builder().labels(of("region", "cn")).value(54).name("http_success_request").build()}
                        );
                    }
                }
            },
            {
                "sum_service_m",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(51).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).value(3).name("http_success_request").build()
                ).build()),
                "http_success_request.sum(['idc', 'region']).service(['idc' , 'region'])",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newService("t1.us"),
                            new Sample[] {Sample.builder().labels(of()).value(150).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newService("t3.cn"),
                            new Sample[] {Sample.builder().labels(of()).value(54).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newService("t1"),
                            new Sample[] {Sample.builder().labels(of()).value(50).name("http_success_request").build()}
                        );
                    }
                }
            },
            {
                "sum_service_endpiont",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(51).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).value(3).name("http_success_request").build()
                ).build()),
                "http_success_request.sum(['region', 'idc']).endpoint(['idc'] , ['region'])",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newEndpoint("t1", "us"),
                            new Sample[] {Sample.builder().labels(of()).value(150).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newEndpoint("t3", "cn"),
                            new Sample[] {Sample.builder().labels(of()).value(54).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newEndpoint("t1", ""),
                            new Sample[] {Sample.builder().labels(of()).value(50).name("http_success_request").build()}
                        );
                    }
                }
            },

            {
                "sum_service_endpiont_labels",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(51).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).value(3).name("http_success_request").build()
                ).build()),
                "http_success_request.sum(['region', 'idc' , 'instance']).endpoint(['idc'] , ['region'])",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newEndpoint("t1", "us"),
                            new Sample[] {
                                Sample.builder().labels(of("instance", "")).value(50).name("http_success_request").build(),
                                Sample.builder().labels(of("instance", "10.0.0.1")).value(100).name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newEndpoint("t3", "cn"),
                            new Sample[] {
                                Sample.builder().labels(of("instance", "")).value(51).name("http_success_request").build(),
                                Sample.builder().labels(of("instance", "10.0.0.1")).value(3).name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newEndpoint("t1", ""),
                            new Sample[] {Sample.builder().labels(of("instance", "")).value(50).name("http_success_request").build()}
                        );
                    }
                }
            },
            {
                "sum_service_endpiont_labels_m",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "product")).value(51).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "catalog")).value(50).name("http_success_request").build(),
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
                "http_success_request.sum(['region', 'idc' , 'svc' , 'instance']).endpoint(['idc'] , ['region','svc'])",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newEndpoint("t1", "us.catalog"),
                            new Sample[] {
                                Sample.builder().labels(of("instance", "")).value(50).name("http_success_request").build(),
                                Sample.builder().labels(of("instance", "10.0.0.1")).value(100).name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newEndpoint("t3", "cn.product"),
                            new Sample[] {
                                Sample.builder().labels(of("instance", "")).value(51).name("http_success_request").build(),
                                Sample.builder().labels(of("instance", "10.0.0.1")).value(3).name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newEndpoint("t1", ""),
                            new Sample[] {Sample.builder().labels(of("instance", "")).value(50).name("http_success_request").build()}
                        );
                    }
                }
            },
            {
                "sum_service_instance",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(51).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).value(3).name("http_success_request").build()
                ).build()),
                "http_success_request.sum(['region', 'idc']).instance(['idc'] , ['region'])",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newServiceInstance("t1", "us"),
                            new Sample[] {Sample.builder().labels(of()).value(150).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newServiceInstance("t3", "cn"),
                            new Sample[] {Sample.builder().labels(of()).value(54).name("http_success_request").build()}
                        );
                        put(
                            MeterEntity.newServiceInstance("t1", ""),
                            new Sample[] {Sample.builder().labels(of()).value(50).name("http_success_request").build()}
                        );
                    }
                }
            },
            {
                "sum_service_instance_labels",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(51).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).value(3).name("http_success_request").build()
                ).build()),
                "http_success_request.sum(['region', 'idc' , 'instance']).instance(['idc'] , ['region'])",
                false,
                new HashMap<MeterEntity, Sample[]>() {
                    {
                        put(
                            MeterEntity.newServiceInstance("t1", "us"),
                            new Sample[] {
                                Sample.builder().labels(of("instance", "")).value(50).name("http_success_request").build(),
                                Sample.builder().labels(of("instance", "10.0.0.1")).value(100).name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newServiceInstance("t3", "cn"),
                            new Sample[] {
                                Sample.builder().labels(of("instance", "")).value(51).name("http_success_request").build(),
                                Sample.builder().labels(of("instance", "10.0.0.1")).value(3).name("http_success_request").build()
                            }
                        );
                        put(
                            MeterEntity.newServiceInstance("t1", ""),
                            new Sample[] {Sample.builder().labels(of("instance", "")).value(50).name("http_success_request").build()}
                        );
                    }
                }
            },
            });
    }

    @Test
    public void test() {
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
        assertThat(r.isSuccess(), is(true));
        Map<MeterEntity, Sample[]> meterSamplesR = r.getData().context.getMeterSamples();
        meterSamplesR.forEach((meterEntity, samples) -> {
            assertThat(samples, is(want.get(meterEntity)));
        });
    }
}
